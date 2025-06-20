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
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.ClientCalls;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
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
final class BlockNode implements Comparable<BlockNode> {

    public static final Function<BlockNodeProperties, ManagedChannelBuilder<?>> DEFAULT_CHANNEL_BUILDER_PROVIDER =
            blockNodeProperties -> {
                var builder = ManagedChannelBuilder.forTarget(blockNodeProperties.getEndpoint());
                if (blockNodeProperties.getPort() != 443) {
                    builder.usePlaintext();
                } else {
                    builder.useTransportSecurity();
                }

                return builder;
            };

    private static final Comparator<BlockNode> COMPARATOR = Comparator.comparing(blockNode -> blockNode.properties);
    private static final long INFINITE_END_BLOCK_NUMBER = -1;
    private static final ServerStatusRequest SERVER_STATUS_REQUEST = ServerStatusRequest.getDefaultInstance();

    @Getter
    private boolean active = true;

    private final ManagedChannel channel;
    private final AtomicInteger errors = new AtomicInteger();
    private final BlockNodeProperties properties;
    private final AtomicReference<Instant> readmitTime = new AtomicReference<>(Instant.now());
    private final StreamProperties streamProperties;

    BlockNode(
            ManagedChannelBuilderProvider channelBuilderProvider,
            BlockNodeProperties properties,
            StreamProperties streamProperties) {
        this.channel = channelBuilderProvider
                .get(properties)
                .maxInboundMessageSize(
                        (int) streamProperties.getMaxStreamResponseSize().toBytes())
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
                    .withDeadlineAfter(streamProperties.getResponseTimeout());
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
            var assembler = new BlockAssembler(blockTimeout);
            var request = SubscribeStreamRequest.newBuilder()
                    .setEndBlockNumber(INFINITE_END_BLOCK_NUMBER)
                    .setStartBlockNumber(blockNumber)
                    .build();
            grpcCall.set(ClientCalls.blockingV2ServerStreamingCall(
                    channel,
                    BlockStreamSubscribeServiceGrpc.getSubscribeBlockStreamMethod(),
                    CallOptions.DEFAULT,
                    request));
            SubscribeStreamResponse response;

            while ((response = grpcCall.get().read(assembler.timeout(), TimeUnit.MILLISECONDS)) != null) {
                boolean serverSuccess = false;
                switch (response.getResponseCase()) {
                    case BLOCK_ITEMS -> {
                        var streamedBlock = assembler.assemble(response.getBlockItems());
                        if (streamedBlock != null) {
                            onStreamedBlock.accept(streamedBlock);
                        }
                    }
                    case STATUS -> {
                        var status = response.getStatus();
                        if (status == SubscribeStreamResponse.Code.SUCCESS) {
                            // The server may end the stream gracefully for various reasons, and this shouldn't be
                            // treated as an error.
                            log.info("Block server ended the subscription with {}", status);
                            serverSuccess = true;
                            break;
                        }

                        throw new BlockStreamException("Received status " + response.getStatus() + " from block node");
                    }
                    default -> throw new BlockStreamException("Unknown response case " + response.getResponseCase());
                }

                errors.set(0);

                if (serverSuccess) {
                    break;
                }
            }
        } catch (BlockStreamException ex) {
            onError();
            throw ex;
        } catch (Exception ex) {
            onError();
            throw new BlockStreamException(ex);
        } finally {
            if (grpcCall.get() != null) {
                grpcCall.get().cancel("unsubscribe", null);
            }
        }
    }

    @Override
    public int compareTo(BlockNode other) {
        return COMPARATOR.compare(this, other);
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
        if (errors.incrementAndGet() >= streamProperties.getMaxSubscribeAttempts()) {
            active = false;
            errors.set(0);
            readmitTime.set(Instant.now().plus(streamProperties.getReadmitDelay()));
            log.warn(
                    "Marking connection to {} as inactive after {} attempts",
                    this,
                    streamProperties.getMaxSubscribeAttempts());
        }
    }

    private class BlockAssembler {

        private long loadStart;
        private final List<List<BlockItem>> pending = new ArrayList<>();
        private int pendingCount = 0;
        private final Stopwatch stopwatch;
        private final Duration timeout;

        BlockAssembler(Duration timeout) {
            this.stopwatch = Stopwatch.createUnstarted();
            this.timeout = timeout;
        }

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
            stopwatch.reset();

            return new StreamedBlock(block, loadStart);
        }

        long timeout() {
            if (!stopwatch.isRunning()) {
                stopwatch.start();
                return timeout.toMillis();
            }

            return timeout.toMillis() - stopwatch.elapsed(TimeUnit.MILLISECONDS);
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
