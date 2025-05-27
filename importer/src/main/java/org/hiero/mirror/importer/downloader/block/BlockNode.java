// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_PROOF;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.RECORD_FILE;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.mirror.importer.exception.BlockStreamException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@CustomLog
final class BlockNode {

    private static final ServerStatusRequest SERVER_STATUS_REQUEST = ServerStatusRequest.getDefaultInstance();

    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ManagedChannelBuilder<?> channelBuilder;
    private final AtomicInteger errors = new AtomicInteger();
    private final BlockNodeProperties properties;
    private final AtomicReference<Instant> readmitTime = new AtomicReference<>(Instant.now());
    private final StreamProperties streamProperties;

    BlockNode(BlockNodeProperties properties, StreamProperties streamProperties) {
        this.properties = properties;
        this.streamProperties = streamProperties;
        var endpoint = properties.getEndpoint();
        var channelBuilder = properties.isInProcess()
                ? InProcessChannelBuilder.forName(endpoint)
                : ManagedChannelBuilder.forTarget(endpoint);
        this.channelBuilder = channelBuilder
                .maxInboundMessageSize(streamProperties.getMaxStreamResponseSize())
                .usePlaintext();
    }

    public boolean hasBlock(long blockNumber) {
        var channel = channelBuilder.build();

        try {
            var blockNodeService = BlockNodeServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(streamProperties.getStatusTimeout());
            var response = blockNodeService.serverStatus(SERVER_STATUS_REQUEST);
            return blockNumber >= response.getFirstAvailableBlock() && blockNumber <= response.getLastAvailableBlock();
        } catch (Exception ex) {
            log.error("Failed to get block node server status", ex);
            return false;
        } finally {
            channel.shutdownNow();
        }
    }

    public boolean isActive() {
        return active.get();
    }

    public void onError() {
        if (errors.incrementAndGet() >= streamProperties.getMaxSubscribeAttempts()) {
            log.warn(
                    "Failed to stream blocks from {} {} times consecutively, mark it inactive",
                    this,
                    streamProperties.getMaxSubscribeAttempts());
            active.set(false);
            errors.set(0);
            readmitTime.set(Instant.now().plus(streamProperties.getReadmitDelay()));
        }
    }

    public Flux<StreamedBlock> stream(long blockNumber) {
        var context = new AtomicReference<SubscriptionContext>();
        return Flux.<StreamedBlock>create(sink -> {
                    var channel = channelBuilder.build();
                    var call = channel.newCall(
                            BlockStreamSubscribeServiceGrpc.getSubscribeBlockStreamMethod(), CallOptions.DEFAULT);
                    context.set(new SubscriptionContext(call, channel));
                    var request = SubscribeStreamRequest.newBuilder()
                            .setStartBlockNumber(blockNumber)
                            .build();
                    var responseObserver = new ResponseObserver(sink);
                    ClientCalls.asyncServerStreamingCall(call, request, responseObserver);
                })
                .doOnNext(b -> errors.set(0))
                .doFinally(s -> {
                    if (context.get() != null) {
                        context.get().onCompleted();
                    }
                });
    }

    @Override
    public String toString() {
        return String.format("BlockNode(%s)", properties.getEndpoint());
    }

    public BlockNode tryReadmit(boolean force) {
        if (!active.get() && (force || Instant.now().isAfter(readmitTime.get()))) {
            active.set(true);
        }

        return this;
    }

    @RequiredArgsConstructor
    private class ResponseObserver implements StreamObserver<SubscribeStreamResponse> {

        private long loadStart;
        private final List<List<BlockItem>> pending = new ArrayList<>();
        private int pendingCount = 0;
        private final FluxSink<StreamedBlock> sink;

        @Override
        public void onCompleted() {
            sink.complete();
        }

        @Override
        public void onError(Throwable t) {
            sink.error(t);
        }

        @Override
        public void onNext(SubscribeStreamResponse response) {
            switch (response.getResponseCase()) {
                case BLOCK_ITEMS -> onBlockItemSet(response.getBlockItems());
                case STATUS ->
                    sink.error(
                            new BlockStreamException("Received status " + response.getStatus() + " from block node"));
                default -> sink.error(new BlockStreamException("Unknown response case " + response.getResponseCase()));
            }
        }

        private void onBlockItemSet(BlockItemSet blockItemSet) {
            var blockItems = blockItemSet.getBlockItemsList();
            if (blockItems.isEmpty()) {
                log.warn("Received empty BlockItemSet from block node");
                return;
            }

            var firstItemCase = blockItems.getFirst().getItemCase();
            var error = tryAppend(blockItems, firstItemCase);
            if (error != null) {
                sink.error(error);
                return;
            }

            if (firstItemCase == BLOCK_HEADER || firstItemCase == RECORD_FILE) {
                loadStart = System.currentTimeMillis();
            }

            if (firstItemCase == RECORD_FILE || blockItems.getLast().getItemCase() == BLOCK_PROOF) {
                onCompletedBlock();
            }
        }

        private void onCompletedBlock() {
            List<BlockItem> block;
            if (pending.size() == 1) {
                block = pending.getFirst();
            } else {
                // assemble when there are more than one BlockItemSet
                block = new ArrayList<>();
                for (var blockItems : pending) {
                    block.addAll(blockItems);
                }
            }

            sink.next(new StreamedBlock(block, loadStart));
            pending.clear();
            pendingCount = 0;
        }

        private BlockStreamException tryAppend(List<BlockItem> blockItems, BlockItem.ItemCase firstItemCase) {
            if ((firstItemCase == BLOCK_HEADER || firstItemCase == RECORD_FILE) && !pending.isEmpty()) {
                return new BlockStreamException(
                        "Received block items of a new block while the previous block is still pending");
            } else if (firstItemCase != BLOCK_HEADER && firstItemCase != RECORD_FILE && pending.isEmpty()) {
                return new BlockStreamException("Incorrect first block item case " + firstItemCase);
            } else if (firstItemCase == RECORD_FILE && blockItems.size() > 1) {
                return new BlockStreamException(
                        "The first block item is record file and there are more than one block items");
            }

            pending.add(blockItems);
            pendingCount += blockItems.size();
            if (pendingCount > streamProperties.getMaxBlockItems()) {
                return new BlockStreamException(String.format(
                        "Too many block items in a pending block: received %d, limit %d",
                        pendingCount, streamProperties.getMaxBlockItems()));
            }

            return null;
        }
    }

    public record StreamedBlock(List<BlockItem> blockItems, long loadStart) {}

    private record SubscriptionContext(
            ClientCall<SubscribeStreamRequest, SubscribeStreamResponse> call, ManagedChannel channel) {

        void onCompleted() {
            log.debug("Unsubscribe from block node");
            call.cancel("unsubscribe", null);
            channel.shutdownNow();
        }
    }
}
