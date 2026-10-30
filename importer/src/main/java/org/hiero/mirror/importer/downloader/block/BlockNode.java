// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.hiero.block.internal.protoc.BlockItemUnparsed.ItemCase.BLOCK_HEADER;
import static org.hiero.mirror.common.util.DomainUtils.parseProtobuf;
import static org.hiero.mirror.importer.downloader.block.scheduler.Scheduler.EARLIEST_AVAILABLE_BLOCK_NUMBER;

import com.google.common.base.Stopwatch;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import io.grpc.CallOptions;
import io.grpc.ClientStreamTracer;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.protobuf.ProtoUtils;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.block.api.protoc.BlockEnd;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockRange;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusDetailResponse;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.block.internal.protoc.BlockItemSetUnparsed;
import org.hiero.block.internal.protoc.BlockItemUnparsed;
import org.hiero.block.internal.protoc.SubscribeStreamResponseUnparsed;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.common.domain.node.RegisteredServiceEndpoint.BlockNodeApi;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.block.scheduler.Latency;
import org.hiero.mirror.importer.downloader.block.scheduler.Scheduler;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@CustomLog
@NullMarked
public final class BlockNode implements AutoCloseable, Comparable<BlockNode> {

    static final String ERROR_METRIC_NAME = "hiero.mirror.importer.stream.error";

    private static final Comparator<BlockNode> COMPARATOR = Comparator.comparing(BlockNode::getProperties);
    private static final ServerStatusRequest SERVER_STATUS_REQUEST = ServerStatusRequest.getDefaultInstance();
    // The generated subscribeBlockStream method, except that the response is parsed as SubscribeStreamResponseUnparsed
    // to keep the block items' serialized bytes as received
    private static final MethodDescriptor<SubscribeStreamRequest, SubscribeStreamResponseUnparsed>
            SUBSCRIBE_BLOCK_STREAM_METHOD = BlockStreamSubscribeServiceGrpc.getSubscribeBlockStreamMethod().toBuilder(
                            ProtoUtils.marshaller(SubscribeStreamRequest.getDefaultInstance()),
                            ProtoUtils.marshaller(SubscribeStreamResponseUnparsed.getDefaultInstance()))
                    .build();

    private final AtomicInteger errors = new AtomicInteger();
    private final Counter errorsMetric;
    private final BiConsumer<String, BlockingClientCall<?, ?>> grpcBufferDisposer;
    private final String name;

    @Getter
    private final String statusEndpoint;

    @Getter
    private final Latency latency = new Latency();

    @Getter
    private final String subscribeStreamName;

    @Getter
    private final BlockNodeProperties properties;

    private final AtomicReference<Instant> readmitTime = new AtomicReference<>(Instant.now());
    private final ManagedChannel statusChannel;
    private final StreamProperties streamProperties;
    private final ManagedChannel subscribeStreamChannel;

    @Getter
    private volatile boolean active = true;

    public BlockNode(
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final BiConsumer<String, BlockingClientCall<?, ?>> grpcBufferDisposer,
            final MeterRegistry meterRegistry,
            final BlockNodeProperties properties,
            final StreamProperties streamProperties) {
        this.grpcBufferDisposer = grpcBufferDisposer;
        this.properties = properties;
        this.streamProperties = streamProperties;

        final var statusEndpoint = getEndpoint(BlockNodeApi.STATUS, properties.getEndpoints());
        final var subscribeStreamEndpoint = getEndpoint(BlockNodeApi.SUBSCRIBE_STREAM, properties.getEndpoints());
        statusChannel = buildChannel(channelBuilderProvider, statusEndpoint, streamProperties);
        this.statusEndpoint = statusEndpoint.toString();

        if (subscribeStreamEndpoint == statusEndpoint) {
            subscribeStreamChannel = statusChannel;
        } else {
            subscribeStreamChannel = buildChannel(channelBuilderProvider, subscribeStreamEndpoint, streamProperties);
        }

        errorsMetric = Counter.builder(ERROR_METRIC_NAME)
                .description("The number of errors that occurred while streaming from a particular block node.")
                .tag("type", StreamType.BLOCK.toString())
                .tag("block_node", this.statusEndpoint)
                .register(meterRegistry);
        name = String.format("BlockNode(%s)", this.statusEndpoint);
        subscribeStreamName = String.format("BlockNode(%s)", subscribeStreamEndpoint);
    }

    @Override
    public void close() {
        shutdownChannel(statusChannel);

        if (subscribeStreamChannel != statusChannel) {
            shutdownChannel(subscribeStreamChannel);
        }
    }

    /**
     * Checks the block node's available block ranges for the requested block.
     *
     * @param blockNumber The block number to look for, or {@link Scheduler#EARLIEST_AVAILABLE_BLOCK_NUMBER} to ask for
     *                    the node's earliest available block
     * @return The block number the node can serve, or empty when it can't serve the request
     */
    public Optional<Long> getBlockOrEarliest(final long blockNumber) {
        try {
            final var response = getServerStatusDetail();
            if (blockNumber == EARLIEST_AVAILABLE_BLOCK_NUMBER) {
                return Optional.ofNullable(findEarliest(response));
            }

            return hasBlock(response, blockNumber) ? Optional.of(blockNumber) : Optional.empty();
        } catch (final Exception ex) {
            log.error("Failed to get server status detail for {}: {}", this, ex.getMessage());
            onError(isUnreachable(ex));
            return Optional.empty();
        }
    }

    /**
     * Checks whether the block node has the block, telling an unreachable node apart from a missing block. Unlike
     * {@link #getBlockOrEarliest}, a failure doesn't count toward deactivating the node.
     */
    public BlockStatus getBlockStatus(final long blockNumber) {
        try {
            return hasBlock(getServerStatusDetail(), blockNumber) ? BlockStatus.AVAILABLE : BlockStatus.NOT_AVAILABLE;
        } catch (final Exception ex) {
            log.debug("Failed to get server status detail for {}", this, ex);
            return BlockStatus.UNREACHABLE;
        }
    }

    /**
     * Streams a single block to measure latency. Unlike {@link #streamBlocks}, a failure doesn't count toward
     * deactivating the node.
     */
    public void probeBlock(
            final long blockNumber,
            final BiFunction<BlockStream, String, Boolean> onBlockStream,
            final Duration timeout) {
        stream(blockNumber, blockNumber, onBlockStream, timeout, false);
    }

    public void streamBlocks(
            final long blockNumber,
            @Nullable final Long endBlockNumber,
            final BiFunction<BlockStream, String, Boolean> onBlockStream,
            final Duration timeout) {
        stream(blockNumber, endBlockNumber, onBlockStream, timeout, true);
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
            final BlockNodeProperties.ServiceEndpoint serviceEndpoint,
            final StreamProperties streamProperties) {
        final int maxInboundMessageSize =
                (int) streamProperties.getMaxStreamResponseSize().toBytes();
        return channelBuilderProvider
                .get(serviceEndpoint.getHost(), serviceEndpoint.getPort(), serviceEndpoint.isRequiresTls())
                .keepAliveTime(streamProperties.getKeepAliveTime().toMillis(), TimeUnit.MILLISECONDS)
                .keepAliveTimeout(streamProperties.getKeepAliveTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .keepAliveWithoutCalls(streamProperties.isKeepAliveWithoutCalls())
                .maxInboundMessageSize(maxInboundMessageSize)
                .build();
    }

    private static @Nullable Long findEarliest(final ServerStatusDetailResponse response) {
        Long earliest = null;
        for (final var range : response.getAvailableRangesList()) {
            if (isValid(range)) {
                earliest = earliest == null ? range.getRangeStart() : Math.min(earliest, range.getRangeStart());
            }
        }

        return earliest;
    }

    private static BlockNodeProperties.ServiceEndpoint getEndpoint(
            final BlockNodeApi api, final Collection<BlockNodeProperties.ServiceEndpoint> endpoints) {
        for (final var endpoint : endpoints) {
            if (endpoint.getApis().contains(api)) {
                return endpoint;
            }
        }

        throw new IllegalStateException("Block node doesn't provide %s API".formatted(api));
    }

    private static boolean hasBlock(final ServerStatusDetailResponse response, final long blockNumber) {
        for (final var range : response.getAvailableRangesList()) {
            if (isValid(range) && blockNumber >= range.getRangeStart() && blockNumber <= range.getRangeEnd()) {
                return true;
            }
        }

        return false;
    }

    private static boolean isUnreachable(final Throwable throwable) {
        final var code = Status.fromThrowable(throwable).getCode();
        return code == Status.Code.DEADLINE_EXCEEDED || code == Status.Code.UNAVAILABLE;
    }

    private static boolean isValid(final BlockRange range) {
        return range.getRangeStart() >= 0 && range.getRangeEnd() >= range.getRangeStart();
    }

    private ServerStatusDetailResponse getServerStatusDetail() {
        return BlockNodeServiceGrpc.newBlockingStub(statusChannel)
                .withDeadlineAfter(streamProperties.getResponseTimeout())
                .serverStatusDetail(SERVER_STATUS_REQUEST);
    }

    /**
     * Counts an error, and deactivates the node until the readmit time when it's unreachable or the errors reach
     * maxSubscribeAttempts.
     */
    private void onError(final boolean unreachable) {
        errorsMetric.increment();
        if (!unreachable && errors.incrementAndGet() < streamProperties.getMaxSubscribeAttempts()) {
            return;
        }

        active = false;
        errors.set(0);
        readmitTime.set(Instant.now().plus(streamProperties.getReadmitDelay()));
        if (unreachable) {
            log.warn("Marking connection to {} as inactive since it's unreachable", this);
        } else {
            log.warn(
                    "Marking connection to {} as inactive after {} attempts",
                    this,
                    streamProperties.getMaxSubscribeAttempts());
        }
    }

    private void shutdownChannel(final ManagedChannel channel) {
        if (channel.isShutdown()) {
            return;
        }

        channel.shutdown();
        try {
            if (!channel.awaitTermination(streamProperties.getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                channel.shutdownNow();
            }
        } catch (final InterruptedException _) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void stream(
            final long blockNumber,
            @Nullable final Long endBlockNumber,
            final BiFunction<BlockStream, String, Boolean> onBlockStream,
            final Duration timeout,
            final boolean trackErrors) {
        BlockingClientCall<SubscribeStreamRequest, SubscribeStreamResponseUnparsed> grpcCall = null;
        boolean responded = false;

        try {
            final long effectiveEndBlockNumber = endBlockNumber == null ? -1L : endBlockNumber;
            final var uncompressedBytes = new AtomicLong();
            final var assembler =
                    new BlockAssembler(onBlockStream, effectiveEndBlockNumber, timeout, uncompressedBytes);
            final var request = SubscribeStreamRequest.newBuilder()
                    .setEndBlockNumber(effectiveEndBlockNumber)
                    .setStartBlockNumber(blockNumber)
                    .build();
            grpcCall = ClientCalls.blockingV2ServerStreamingCall(
                    subscribeStreamChannel,
                    SUBSCRIBE_BLOCK_STREAM_METHOD,
                    CallOptions.DEFAULT.withStreamTracerFactory(new UncompressedSizeTracerFactory(uncompressedBytes)),
                    request);
            SubscribeStreamResponseUnparsed response;

            boolean running = true;
            while (running && (response = grpcCall.read(assembler.timeout(), TimeUnit.MILLISECONDS)) != null) {
                responded = true;
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
                            log.info("{} ended the subscription with {}", subscribeStreamName, status);
                            running = false;
                            break;
                        }

                        throw new BlockStreamException(
                                "Received status " + response.getStatus() + " from " + subscribeStreamName);
                    }
                    default ->
                        throw new BlockStreamException(
                                "Unknown response case " + response.getResponseCase() + " from " + subscribeStreamName);
                }
            }

            if (trackErrors) {
                // Only clear errors when the subscription ends gracefully
                errors.set(0);
            }
        } catch (final BlockStreamException ex) {
            if (trackErrors) {
                onError(false);
            }

            throw ex;
        } catch (final Exception ex) {
            if (trackErrors) {
                // A node that drops the connection mid-stream was reachable, e.g. it may be restarting
                onError(!responded && isUnreachable(ex));
            }

            throw new BlockStreamException(ex);
        } finally {
            if (grpcCall != null) {
                grpcCall.cancel("unsubscribe", null);
                grpcBufferDisposer.accept(subscribeStreamName, grpcCall);
            }
        }
    }

    /**
     * A stream tracer factory that accumulates the decompressed size of inbound messages.
     */
    @RequiredArgsConstructor
    private static final class UncompressedSizeTracerFactory extends ClientStreamTracer.Factory {

        private final AtomicLong uncompressedBytes;

        @Override
        public ClientStreamTracer newClientStreamTracer(
                final ClientStreamTracer.StreamInfo info, final Metadata headers) {
            return new ClientStreamTracer() {
                @Override
                public void inboundUncompressedSize(final long bytes) {
                    uncompressedBytes.addAndGet(bytes);
                }
            };
        }
    }

    private final class BlockAssembler {

        private final BiFunction<BlockStream, String, Boolean> blockStreamConsumer;
        private final long endBlockNumber;
        private final List<List<BlockItemUnparsed>> pending = new ArrayList<>();
        private final Stopwatch stopwatch;
        private final Duration timeout;
        private final AtomicLong uncompressedBytes;
        private long blockStartBytes = 0;
        private long loadStart;
        private int pendingCount = 0;

        BlockAssembler(
                final BiFunction<BlockStream, String, Boolean> blockStreamConsumer,
                final long endBlockNumber,
                final Duration timeout,
                final AtomicLong uncompressedBytes) {
            this.blockStreamConsumer = blockStreamConsumer;
            this.endBlockNumber = endBlockNumber;
            this.stopwatch = Stopwatch.createUnstarted();
            this.timeout = timeout;
            this.uncompressedBytes = uncompressedBytes;
        }

        void onBlockItemSet(final BlockItemSetUnparsed blockItemSet) {
            final var blockItems = blockItemSet.getBlockItemsList();
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
            // Note the size includes the extra bytes from the BlockItemSet message
            final int blockSize = (int) (uncompressedBytes.get() - blockStartBytes);
            blockStartBytes = uncompressedBytes.get();

            if (pending.isEmpty()) {
                Utility.handleRecoverableError(
                        "Received end-of-block message for block {} while there's no pending block items", blockNumber);
                return false;
            }

            final var blockHeader =
                    parseProtobuf(pending.getFirst().getFirst().getBlockHeader(), BlockHeader::parseFrom);
            if (blockHeader.getNumber() != blockNumber) {
                Utility.handleRecoverableError(
                        "Block number mismatch in BlockHeader({}) and EndOfBlock({})",
                        blockHeader.getNumber(),
                        blockNumber);
            }

            final long blockCompleteTime = System.currentTimeMillis();
            final List<BlockItemUnparsed> block;
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
            final var blockStream = new BlockStream(block, blockCompleteTime, null, filename, loadStart, blockSize);
            log.info("Streamed block {} from {}", blockNumber, subscribeStreamName);

            // when either condition becomes true, inform the caller to stop sending items for assembling
            return blockStreamConsumer.apply(blockStream, subscribeStreamName)
                    || blockHeader.getNumber() == endBlockNumber;
        }

        long timeout() {
            if (!stopwatch.isRunning()) {
                stopwatch.start();
                return timeout.toMillis();
            }

            return timeout.toMillis() - stopwatch.elapsed(TimeUnit.MILLISECONDS);
        }

        private void append(final List<BlockItemUnparsed> blockItems, final BlockItemUnparsed.ItemCase firstItemCase) {
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

            final long blockSize = uncompressedBytes.get() - blockStartBytes;
            final long maxBlockSize = streamProperties.getMaxBlockSize().toBytes();
            if (blockSize > maxBlockSize) {
                throw new BlockStreamException(
                        String.format("Pending block too large: received %d bytes, limit %d", blockSize, maxBlockSize));
            }
        }
    }

    public enum BlockStatus {
        AVAILABLE,
        NOT_AVAILABLE,
        UNREACHABLE
    }
}
