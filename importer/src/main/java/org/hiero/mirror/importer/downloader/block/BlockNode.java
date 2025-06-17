// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_PROOF;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.RECORD_FILE;

import com.google.common.base.Stopwatch;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.ClientCalls;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.Getter;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.mirror.importer.exception.BlockStreamException;

@CustomLog
final class BlockNode {

    private static final long INFINITE_END_BLOCK_NUMBER = -1;
    private static final ServerStatusRequest SERVER_STATUS_REQUEST = ServerStatusRequest.getDefaultInstance();

    @Getter
    private boolean active = true;

    private final ManagedChannel channel;
    private int errors = 0;
    private final BlockNodeProperties properties;
    private final AtomicReference<Instant> readmitTime = new AtomicReference<>(Instant.now());
    private final StreamProperties streamProperties;

    BlockNode(BlockNodeProperties properties, StreamProperties streamProperties) {
        var endpoint = properties.getEndpoint();
        var channelBuilder = properties.isInProcess()
                ? InProcessChannelBuilder.forName(endpoint)
                : ManagedChannelBuilder.forTarget(endpoint);
        this.channel = channelBuilder
                .maxInboundMessageSize(streamProperties.getMaxStreamResponseSize())
                .usePlaintext()
                .build();
        this.properties = properties;
        this.streamProperties = streamProperties;
    }

    public void destroy() {
        if (channel.isShutdown()) {
            return;
        }

        channel.shutdown();
    }

    public boolean hasBlock(long blockNumber) {
        try {
            var blockNodeService = BlockNodeServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(streamProperties.getStatusTimeout());
            var response = blockNodeService.serverStatus(SERVER_STATUS_REQUEST);
            return blockNumber >= response.getFirstAvailableBlock() && blockNumber <= response.getLastAvailableBlock();
        } catch (Exception ex) {
            log.error("Failed to get block node server status", ex);
            return false;
        }
    }

    public void streamBlocks(long blockNumber, Duration blockTimeout, Consumer<StreamedBlock> onStreamedBlock) {
        var grpcCall = new AtomicReference<BlockingClientCall<SubscribeStreamRequest, SubscribeStreamResponse>>();

        try {
            var assembler = new BlockAssembler();
            var request = SubscribeStreamRequest.newBuilder()
                    .setEndBlockNumber(INFINITE_END_BLOCK_NUMBER)
                    .setStartBlockNumber(blockNumber)
                    .build();
            grpcCall.set(ClientCalls.blockingV2ServerStreamingCall(
                    channel,
                    BlockStreamSubscribeServiceGrpc.getSubscribeBlockStreamMethod(),
                    CallOptions.DEFAULT,
                    request));
            var timeout = new TimeoutContext(blockTimeout);
            SubscribeStreamResponse response;

            outer:
            while ((response = grpcCall.get().read(timeout.remaining(), TimeUnit.MILLISECONDS)) != null) {
                switch (response.getResponseCase()) {
                    case BLOCK_ITEMS -> {
                        var streamedBlock = assembler.assemble(response.getBlockItems());
                        if (streamedBlock != null) {
                            timeout.reset();
                            onStreamedBlock.accept(streamedBlock);
                        }
                    }
                    case STATUS -> {
                        var status = response.getStatus();
                        if (status == SubscribeStreamResponse.Code.READ_STREAM_SUCCESS) {
                            // The server may end the stream gracefully for various reasons, and this shouldn't be
                            // treated as an error.
                            log.info("Block server ended the subscription with {}", status);
                            break outer;
                        }

                        throw new BlockStreamException("Received status " + response.getStatus() + " from block node");
                    }
                    default -> throw new BlockStreamException("Unknown response case " + response.getResponseCase());
                }
            }
        } catch (Exception ex) {
            onError();

            if (ex instanceof BlockStreamException blockStreamException) {
                throw blockStreamException;
            }

            throw new BlockStreamException(ex);
        } finally {
            if (grpcCall.get() != null) {
                grpcCall.get().cancel("unsubscribe", null);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("BlockNode(%s)", properties.getEndpoint());
    }

    public BlockNode tryReadmit(boolean force) {
        if (!active && (force || Instant.now().isAfter(readmitTime.get()))) {
            active = true;
        }

        return this;
    }

    private void onError() {
        if (++errors >= streamProperties.getMaxSubscribeAttempts()) {
            log.warn(
                    "Failed to stream blocks from {} {} times consecutively, mark it inactive",
                    this,
                    streamProperties.getMaxSubscribeAttempts());
            active = false;
            errors = 0;
            readmitTime.set(Instant.now().plus(streamProperties.getReadmitDelay()));
        }
    }

    private static class TimeoutContext {

        private final Duration timeout;
        private final Stopwatch stopwatch;

        TimeoutContext(Duration timeout) {
            this.stopwatch = Stopwatch.createUnstarted();
            this.timeout = timeout;
        }

        long remaining() {
            if (!stopwatch.isRunning()) {
                stopwatch.start();
                return timeout.toMillis();
            }

            return timeout.toMillis() - stopwatch.elapsed(TimeUnit.MILLISECONDS);
        }

        void reset() {
            stopwatch.reset();
        }
    }

    private class BlockAssembler {

        private long loadStart;
        private final List<List<BlockItem>> pending = new ArrayList<>();
        private int pendingCount = 0;

        StreamedBlock assemble(BlockItemSet blockItemSet) {
            var blockItems = blockItemSet.getBlockItemsList();
            if (blockItems.isEmpty()) {
                log.warn("Received empty BlockItemSet from block node");
                return null;
            }

            var firstItemCase = blockItems.getFirst().getItemCase();
            append(blockItems, firstItemCase);

            if (firstItemCase == BLOCK_HEADER || firstItemCase == RECORD_FILE) {
                loadStart = System.currentTimeMillis();
            }

            if (firstItemCase != RECORD_FILE && blockItems.getLast().getItemCase() != BLOCK_PROOF) {
                return null;
            }

            List<BlockItem> block;
            if (pending.size() == 1) {
                block = pending.getFirst();
            } else {
                // assemble when there are more than one BlockItemSet
                block = new ArrayList<>();
                for (var items : pending) {
                    block.addAll(items);
                }
            }

            pending.clear();
            pendingCount = 0;

            return new StreamedBlock(block, loadStart);
        }

        private void append(List<BlockItem> blockItems, BlockItem.ItemCase firstItemCase) {
            if ((firstItemCase == BLOCK_HEADER || firstItemCase == RECORD_FILE) && !pending.isEmpty()) {
                throw new BlockStreamException(
                        "Received block items of a new block while the previous block is still pending");
            } else if (firstItemCase != BLOCK_HEADER && firstItemCase != RECORD_FILE && pending.isEmpty()) {
                throw new BlockStreamException("Incorrect first block item case " + firstItemCase);
            } else if (firstItemCase == RECORD_FILE && blockItems.size() > 1) {
                throw new BlockStreamException(
                        "The first block item is record file and there are more than one block items");
            }

            pending.add(blockItems);
            pendingCount += blockItems.size();
            if (pendingCount > streamProperties.getMaxBlockItems()) {
                throw new BlockStreamException(String.format(
                        "Too many block items in a pending block: received %d, limit %d",
                        pendingCount, streamProperties.getMaxBlockItems()));
            }
        }
    }

    public record StreamedBlock(List<BlockItem> blockItems, long loadStart) {}
}
