// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;
import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.RECORD_FILE;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.ClientCalls;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.hiero.block.api.protoc.BlockEnd;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;

@CustomLog
@NullMarked
final class BlockNode implements AutoCloseable, Comparable<BlockNode> {

    static final String ERROR_METRIC_NAME = "hiero.mirror.importer.stream.error";
    private static final Comparator<BlockNode> COMPARATOR = Comparator.comparing(blockNode -> blockNode.properties);
    private static final Range<Long> EMPTY_BLOCK_RANGE = Range.closedOpen(0L, 0L);
    private static final ServerStatusRequest SERVER_STATUS_REQUEST = ServerStatusRequest.getDefaultInstance();

    private final ManagedChannel statusChannel;
    private final ManagedChannel streamingChannel;
    private final AtomicInteger errors = new AtomicInteger();
    private final Consumer<BlockingClientCall<?, ?>> grpcBufferDisposer;

    @Getter
    private final BlockNodeProperties properties;

    private final AtomicReference<Instant> readmitTime = new AtomicReference<>(Instant.now());
    private final StreamProperties streamProperties;

    private final Counter errorsMetric;

    @Getter
    private boolean active = true;

    BlockNode(
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final Consumer<BlockingClientCall<?, ?>> grpcBufferDisposer,
            final BlockNodeProperties properties,
            final StreamProperties streamProperties,
            final MeterRegistry meterRegistry) {
        final int maxInboundMessageSize =
                (int) streamProperties.getMaxStreamResponseSize().toBytes();
        this.statusChannel = channelBuilderProvider
                .get(properties.getHost(), properties.getStatusPort())
                .maxInboundMessageSize(maxInboundMessageSize)
                .build();

        if (properties.getStatusPort() == properties.getStreamingPort()) {
            this.streamingChannel = this.statusChannel;
        } else {
            this.streamingChannel = channelBuilderProvider
                    .get(properties.getHost(), properties.getStreamingPort())
                    .maxInboundMessageSize(maxInboundMessageSize)
                    .build();
        }

        this.grpcBufferDisposer = grpcBufferDisposer;
        this.properties = properties;
        this.streamProperties = streamProperties;
        this.errorsMetric = Counter.builder(ERROR_METRIC_NAME)
                .description("The number of errors that occurred while streaming from a particular block node.")
                .tag("type", StreamType.BLOCK.toString())
                .tag("block_node", properties.getStatusEndpoint())
                .register(meterRegistry);
    }

    @Override
    public void close() {
        if (!statusChannel.isShutdown()) {
            statusChannel.shutdown();
        }

        if (streamingChannel != statusChannel && !streamingChannel.isShutdown()) {
            streamingChannel.shutdown();
        }
    }

    public Range<Long> getBlockRange() {
        try {
            final var blockNodeService = BlockNodeServiceGrpc.newBlockingStub(statusChannel)
                    .withDeadlineAfter(streamProperties.getResponseTimeout());
            final var response = blockNodeService.serverStatus(SERVER_STATUS_REQUEST);
            final long firstBlockNumber = response.getFirstAvailableBlock();
            return firstBlockNumber != -1
                    ? Range.closed(firstBlockNumber, response.getLastAvailableBlock())
                    : EMPTY_BLOCK_RANGE;
        } catch (Exception ex) {
            log.error("Failed to get server status for {}", this, ex);
            return EMPTY_BLOCK_RANGE;
        }
    }

    public void streamBlocks(
            final long blockNumber,
            final CommonDownloaderProperties commonDownloaderProperties,
            final Consumer<BlockStream> onBlockStream) {
        final var callHolder =
                new AtomicReference<BlockingClientCall<SubscribeStreamRequest, SubscribeStreamResponse>>();

        try {
            final long endBlockNumber = Objects.requireNonNullElse(
                    commonDownloaderProperties.getImporterProperties().getEndBlockNumber(), -1L);
            final var assembler = new BlockAssembler(onBlockStream, commonDownloaderProperties.getTimeout());
            final var request = SubscribeStreamRequest.newBuilder()
                    .setEndBlockNumber(endBlockNumber)
                    .setStartBlockNumber(blockNumber)
                    .build();
            final var grpcCall = ClientCalls.blockingV2ServerStreamingCall(
                    streamingChannel,
                    BlockStreamSubscribeServiceGrpc.getSubscribeBlockStreamMethod(),
                    CallOptions.DEFAULT,
                    request);
            callHolder.set(grpcCall);
            SubscribeStreamResponse response;

            boolean serverSuccess = false;
            while (!serverSuccess && (response = grpcCall.read(assembler.timeout(), TimeUnit.MILLISECONDS)) != null) {
                switch (response.getResponseCase()) {
                    case BLOCK_ITEMS -> assembler.onBlockItemSet(response.getBlockItems());
                    case END_OF_BLOCK -> assembler.onEndOfBlock(response.getEndOfBlock());
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
            final var call = callHolder.get();
            if (call != null) {
                call.cancel("unsubscribe", null);
                grpcBufferDisposer.accept(call);
            }
        }
    }

    @Override
    public int compareTo(final BlockNode other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return String.format("BlockNode(%s)", properties.getStatusEndpoint());
    }

    public BlockNode tryReadmit(final boolean force) {
        if (!active && (force || Instant.now().isAfter(readmitTime.get()))) {
            active = true;
        }

        return this;
    }

    private void onError() {
        errorsMetric.increment();
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

        private final Consumer<BlockStream> blockStreamConsumer;
        private final List<List<BlockItem>> pending = new ArrayList<>();
        private final Stopwatch stopwatch;
        private final Duration timeout;
        private long loadStart;
        private int pendingCount = 0;

        BlockAssembler(final Consumer<BlockStream> blockStreamConsumer, final Duration timeout) {
            this.blockStreamConsumer = blockStreamConsumer;
            this.stopwatch = Stopwatch.createUnstarted();
            this.timeout = timeout;
        }

        void onBlockItemSet(final BlockItemSet blockItemSet) {
            var blockItems = blockItemSet.getBlockItemsList();
            if (blockItems.isEmpty()) {
                log.warn("Received empty BlockItemSet from block node");
                return;
            }

            final var firstItemCase = blockItems.getFirst().getItemCase();
            append(blockItems, firstItemCase);

            if (firstItemCase == BLOCK_HEADER || firstItemCase == RECORD_FILE) {
                loadStart = System.currentTimeMillis();
            }
        }

        void onEndOfBlock(final BlockEnd blockEnd) {
            final long blockNumber = blockEnd.getBlockNumber();
            if (pending.isEmpty()) {
                Utility.handleRecoverableError(
                        "Received end-of-block message for block {} while there's no pending block items", blockNumber);
                return;
            }

            final var firstBlockItem = pending.getFirst().getFirst();
            if (firstBlockItem.getItemCase() == BLOCK_HEADER
                    && firstBlockItem.getBlockHeader().getNumber() != blockNumber) {
                Utility.handleRecoverableError(
                        "Block number mismatch in BlockHeader({}) and EndOfBlock({})",
                        firstBlockItem.getBlockHeader().getNumber(),
                        blockNumber);
            }

            List<BlockItem> block;
            if (pending.size() == 1) {
                block = pending.getFirst();
            } else {
                // assemble when there are more than one BlockItemSet
                block = new ArrayList<>();
                for (final var items : pending) {
                    block.addAll(items);
                }
            }

            pending.clear();
            pendingCount = 0;
            stopwatch.reset();

            final var filename = firstBlockItem.getItemCase() == BLOCK_HEADER
                    ? BlockFile.getFilename(block.getFirst().getBlockHeader().getNumber(), false)
                    : null;
            blockStreamConsumer.accept(new BlockStream(block, null, filename, loadStart));
        }

        long timeout() {
            if (!stopwatch.isRunning()) {
                stopwatch.start();
                return timeout.toMillis();
            }

            return timeout.toMillis() - stopwatch.elapsed(TimeUnit.MILLISECONDS);
        }

        private void append(final List<BlockItem> blockItems, final BlockItem.ItemCase firstItemCase) {
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
