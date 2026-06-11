// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static com.hedera.hapi.block.stream.protoc.BlockItem.ItemCase.BLOCK_HEADER;

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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
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
import org.hiero.mirror.importer.downloader.block.scheduler.Latency;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@CustomLog
@NullMarked
public final class BlockNode implements AutoCloseable, Comparable<BlockNode> {

    public static final Comparator<BlockNode> LATENCY_COMPARATOR =
            Comparator.comparing(BlockNode::getLatency).thenComparing(b -> b.statusEndpoint);

    static final String ERROR_METRIC_NAME = "hiero.mirror.importer.stream.error";

    private static final Comparator<BlockNode> COMPARATOR = Comparator.comparing(BlockNode::getProperties);
    private static final Range<Long> EMPTY_BLOCK_RANGE = Range.closedOpen(0L, 0L);
    private static final ServerStatusRequest SERVER_STATUS_REQUEST = ServerStatusRequest.getDefaultInstance();

    private final AtomicInteger errors = new AtomicInteger();
    private final Counter errorsMetric;
    private final Consumer<BlockingClientCall<?, ?>> grpcBufferDisposer;
    private final String name;

    @Getter
    private final Latency latency = new Latency();

    @Getter
    private final BlockNodeProperties properties;

    private final AtomicReference<Instant> readmitTime = new AtomicReference<>(Instant.now());
    private final ManagedChannel statusChannel;
    private final BlockNodeProperties.ServiceEndpoint statusEndpoint;
    private final StreamProperties streamProperties;
    private final ManagedChannel subscribeStreamChannel;

    @Getter
    private boolean active = true;

    public BlockNode(
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final Consumer<BlockingClientCall<?, ?>> grpcBufferDisposer,
            final MeterRegistry meterRegistry,
            final BlockNodeProperties properties,
            final StreamProperties streamProperties) {
        this.grpcBufferDisposer = grpcBufferDisposer;
        this.properties = properties;
        this.streamProperties = streamProperties;

        final int maxInboundMessageSize =
                (int) streamProperties.getMaxStreamResponseSize().toBytes();
        statusEndpoint = getEndpoint(BlockNodeProperties.Api.STATUS, properties.getEndpoints());
        final var subscribeStreamEndpoint =
                getEndpoint(BlockNodeProperties.Api.SUBSCRIBE_STREAM, properties.getEndpoints());
        statusChannel = buildChannel(channelBuilderProvider, maxInboundMessageSize, statusEndpoint);

        if (subscribeStreamEndpoint == statusEndpoint) {
            subscribeStreamChannel = statusChannel;
        } else {
            subscribeStreamChannel =
                    buildChannel(channelBuilderProvider, maxInboundMessageSize, subscribeStreamEndpoint);
        }

        name = String.format("BlockNode(%s)", statusEndpoint);
        errorsMetric = Counter.builder(ERROR_METRIC_NAME)
                .description("The number of errors that occurred while streaming from a particular block node.")
                .tag("type", StreamType.BLOCK.toString())
                .tag("block_node", statusEndpoint.toString())
                .register(meterRegistry);
    }

    @Override
    public void close() {
        if (!statusChannel.isShutdown()) {
            statusChannel.shutdown();
        }

        if (subscribeStreamChannel != statusChannel) {
            subscribeStreamChannel.shutdown();
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
            @Nullable final Long endBlockNumber,
            final BiFunction<BlockStream, String, Boolean> onBlockStream,
            final Duration timeout) {
        BlockingClientCall<SubscribeStreamRequest, SubscribeStreamResponse> grpcCall = null;

        try {
            final long effectiveEndBlockNumber = endBlockNumber == null ? -1L : endBlockNumber;
            final var assembler = new BlockAssembler(onBlockStream, effectiveEndBlockNumber, timeout);
            final var request = SubscribeStreamRequest.newBuilder()
                    .setEndBlockNumber(effectiveEndBlockNumber)
                    .setStartBlockNumber(blockNumber)
                    .build();
            grpcCall = ClientCalls.blockingV2ServerStreamingCall(
                    subscribeStreamChannel,
                    BlockStreamSubscribeServiceGrpc.getSubscribeBlockStreamMethod(),
                    CallOptions.DEFAULT,
                    request);
            SubscribeStreamResponse response;

            boolean running = true;
            while (running && (response = grpcCall.read(assembler.timeout(), TimeUnit.MILLISECONDS)) != null) {
                switch (response.getResponseCase()) {
                    case BLOCK_ITEMS -> assembler.onBlockItemSet(response.getBlockItems());
                    case END_OF_BLOCK -> {
                        running = !assembler.onEndOfBlock(response.getEndOfBlock());
                        if (!running) {
                            log.debug("Cancelling the subscription");
                        }
                    }
                    case STATUS -> {
                        final var status = response.getStatus();
                        if (status == SubscribeStreamResponse.Code.SUCCESS) {
                            // The server may end the stream gracefully for various reasons, and this shouldn't be
                            // treated as an error.
                            log.info("{} ended the subscription with {}", name, status);
                            running = false;
                            break;
                        }

                        throw new BlockStreamException("Received status " + response.getStatus() + " from " + name);
                    }
                    default ->
                        throw new BlockStreamException(
                                "Unknown response case " + response.getResponseCase() + " from " + name);
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
            if (grpcCall != null) {
                grpcCall.cancel("unsubscribe", null);
                grpcBufferDisposer.accept(grpcCall);
            }
        }
    }

    @Override
    public int compareTo(final BlockNode other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return name;
    }

    public BlockNode tryReadmit(final boolean force) {
        if (!active && (force || Instant.now().isAfter(readmitTime.get()))) {
            active = true;
        }

        return this;
    }

    private static ManagedChannel buildChannel(
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final int maxInboundMessageSize,
            final BlockNodeProperties.ServiceEndpoint serviceEndpoint) {
        return channelBuilderProvider
                .get(serviceEndpoint.getHost(), serviceEndpoint.getPort(), serviceEndpoint.isRequiresTls())
                .maxInboundMessageSize(maxInboundMessageSize)
                .build();
    }

    private static BlockNodeProperties.ServiceEndpoint getEndpoint(
            final BlockNodeProperties.Api api, final Collection<BlockNodeProperties.ServiceEndpoint> endpoints) {
        for (final var endpoint : endpoints) {
            if (endpoint.getApis().contains(api)) {
                return endpoint;
            }
        }

        throw new IllegalStateException("Block node doesn't provide %s API".formatted(api));
    }

    /**
     * If number of failed connections surpass maxAttempts a readmit time(cooldown period)
     * is enforced before the specific node can be called again
     */
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

    private final class BlockAssembler {

        private final BiFunction<BlockStream, String, Boolean> blockStreamConsumer;
        private final long endBlockNumber;
        private final List<List<BlockItem>> pending = new ArrayList<>();
        private final Stopwatch stopwatch;
        private final Duration timeout;
        private long loadStart;
        private int pendingCount = 0;

        BlockAssembler(
                final BiFunction<BlockStream, String, Boolean> blockStreamConsumer,
                final long endBlockNumber,
                final Duration timeout) {
            this.blockStreamConsumer = blockStreamConsumer;
            this.endBlockNumber = endBlockNumber;
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

            if (firstItemCase == BLOCK_HEADER) {
                loadStart = System.currentTimeMillis();
            }
        }

        Boolean onEndOfBlock(final BlockEnd blockEnd) {
            final long blockNumber = blockEnd.getBlockNumber();
            if (pending.isEmpty()) {
                Utility.handleRecoverableError(
                        "Received end-of-block message for block {} while there's no pending block items", blockNumber);
                return false;
            }

            final var blockHeader = pending.getFirst().getFirst().getBlockHeader();
            if (blockHeader.getNumber() != blockNumber) {
                Utility.handleRecoverableError(
                        "Block number mismatch in BlockHeader({}) and EndOfBlock({})",
                        blockHeader.getNumber(),
                        blockNumber);
            }

            final long blockCompleteTime = System.currentTimeMillis();
            final List<BlockItem> block;
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

            final var filename = BlockFile.getFilename(blockNumber, false);
            final var blockStream = new BlockStream(block, blockCompleteTime, null, filename, loadStart);

            // when either condition becomes true, inform the caller to stop sending items for assembling
            return blockStreamConsumer.apply(blockStream, name) || blockHeader.getNumber() == endBlockNumber;
        }

        long timeout() {
            if (!stopwatch.isRunning()) {
                stopwatch.start();
                return timeout.toMillis();
            }

            return timeout.toMillis() - stopwatch.elapsed(TimeUnit.MILLISECONDS);
        }

        private void append(final List<BlockItem> blockItems, final BlockItem.ItemCase firstItemCase) {
            if (firstItemCase == BLOCK_HEADER && !pending.isEmpty()) {
                throw new BlockStreamException(
                        "Received block items of a new block while the previous block is still pending");
            } else if (firstItemCase != BLOCK_HEADER && pending.isEmpty()) {
                throw new BlockStreamException("Incorrect first block item case " + firstItemCase);
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
