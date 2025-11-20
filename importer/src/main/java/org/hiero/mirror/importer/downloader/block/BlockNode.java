// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_PROOF;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.RECORD_FILE;

import com.google.common.base.Stopwatch;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.ClientCalls;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;

@CustomLog
final class BlockNode implements AutoCloseable, Comparable<BlockNode> {

    private static final Comparator<BlockNode> COMPARATOR = Comparator.comparing(blockNode -> blockNode.properties);
    private static final ServerStatusRequest SERVER_STATUS_REQUEST = ServerStatusRequest.getDefaultInstance();
    private static final long UNKNOWN_NODE_ID = -1;

    private final ManagedChannel channel;
    private final AtomicInteger errors = new AtomicInteger();
    private final Consumer<BlockingClientCall<?, ?>> grpcBufferDisposer;
    private final BlockNodeProperties properties;
    private final AtomicReference<Instant> readmitTime = new AtomicReference<>(Instant.now());
    private final StreamProperties streamProperties;

    @Getter
    private boolean active = true;

    BlockNode(
            ManagedChannelBuilderProvider channelBuilderProvider,
            Consumer<BlockingClientCall<?, ?>> grpcBufferDisposer,
            BlockNodeProperties properties,
            StreamProperties streamProperties) {
        this.channel = channelBuilderProvider
                .get(properties)
                .maxInboundMessageSize(
                        (int) streamProperties.getMaxStreamResponseSize().toBytes())
                .build();
        this.grpcBufferDisposer = grpcBufferDisposer;
        this.properties = properties;
        this.streamProperties = streamProperties;
    }

    @Override
    public void close() {
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
            log.error("Failed to get server status for {}", this, ex);
            return false;
        }
    }

    public void streamBlocks(
            long blockNumber,
            CommonDownloaderProperties commonDownloaderProperties,
            Consumer<BlockStream> onBlockStream) {
        var grpcCall = new AtomicReference<BlockingClientCall<SubscribeStreamRequest, SubscribeStreamResponse>>();

        try {
            long endBlockNumber = Objects.requireNonNullElse(
                    commonDownloaderProperties.getImporterProperties().getEndBlockNumber(), -1L);
            var assembler = new BlockAssembler(commonDownloaderProperties.getTimeout());
            var request = SubscribeStreamRequest.newBuilder()
                    .setEndBlockNumber(endBlockNumber)
                    .setStartBlockNumber(blockNumber)
                    .build();
            grpcCall.set(ClientCalls.blockingV2ServerStreamingCall(
                    channel,
                    BlockStreamSubscribeServiceGrpc.getSubscribeBlockStreamMethod(),
                    CallOptions.DEFAULT,
                    request));
            SubscribeStreamResponse response;

            boolean serverSuccess = false;
            while (!serverSuccess
                    && (response = grpcCall.get().read(assembler.timeout(), TimeUnit.MILLISECONDS)) != null) {
                switch (response.getResponseCase()) {
                    case BLOCK_ITEMS -> {
                        var blockStream = assembler.assemble(response.getBlockItems());
                        if (blockStream != null) {
                            onBlockStream.accept(blockStream);
                        }
                    }
                    case END_OF_BLOCK -> {
                        // Marks the end of a block; ignore it for now and handle it in a future block node release when
                        // it becomes required
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
            }
        } catch (BlockStreamException ex) {
            onError();
            throw ex;
        } catch (Exception ex) {
            onError();
            throw new BlockStreamException(ex);
        } finally {
            if (grpcCall.get() != null) {
                final var call = grpcCall.get();
                call.cancel("unsubscribe", null);
                grpcBufferDisposer.accept(call);
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

        private final List<List<BlockItem>> pending = new ArrayList<>();
        private final Stopwatch stopwatch;
        private final Duration timeout;
        private long loadStart;
        private int pendingCount = 0;

        BlockAssembler(Duration timeout) {
            this.stopwatch = Stopwatch.createUnstarted();
            this.timeout = timeout;
        }

        BlockStream assemble(BlockItemSet blockItemSet) {
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

            var filename = firstItemCase != RECORD_FILE
                    ? BlockFile.getFilename(block.getFirst().getBlockHeader().getNumber(), false)
                    : null;
            return new BlockStream(block, null, filename, loadStart, UNKNOWN_NODE_ID);
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
}
