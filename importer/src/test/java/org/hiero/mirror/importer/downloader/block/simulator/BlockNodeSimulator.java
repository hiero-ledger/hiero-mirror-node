// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.simulator;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.ForwardingServerBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.hiero.block.api.protoc.BlockEnd;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.ServerStatusResponse;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.springframework.util.CollectionUtils;

public final class BlockNodeSimulator implements AutoCloseable {

    private List<BlockGenerator.BlockRecord> blocks = Collections.emptyList();
    private int chunksPerBlock = 1;
    private long firstBlockNumber;
    private String host;
    private String hostPrefix;
    private boolean inProcessChannel = true;
    private Duration interval;
    private long lastBlockNumber;
    private final AtomicInteger latency = new AtomicInteger();
    private boolean missingBlock;
    private boolean outOfOrder;
    private boolean useDifferentPorts = true;

    private int priority;
    private Server statusServer;
    private Server streamingServer;
    private boolean started;

    @Getter
    private int statusPort;

    @Getter
    private int streamingPort;

    @Override
    public void close() {
        if (!started) {
            return;
        }

        shutdown(statusServer);
        shutdown(streamingServer);

        started = false;
    }

    public String getEndpoint() {
        validateState(started, "BlockNodeSimulator has not been started");
        return String.format("%s:%d", host, statusPort);
    }

    @SneakyThrows
    public BlockNodeSimulator start() {
        validateState(!started, "BlockNodeSimulator has already been started");
        validateState(!blocks.isEmpty(), "BlockNodeSimulator can't start with empty blocks");

        if (outOfOrder) {
            Collections.shuffle(blocks);
        }

        if (missingBlock) {
            blocks.remove(blocks.size() - 2);
        }

        ForwardingServerBuilder<?> statusServerBuilder;
        ForwardingServerBuilder<?> streamingServerBuilder;
        if (inProcessChannel) {
            host = Objects.requireNonNullElse(hostPrefix, "")
                    + RandomStringUtils.secure().nextAlphabetic(8);
            statusServerBuilder = InProcessServerBuilder.forName(host);
            streamingServerBuilder = statusServerBuilder;
        } else {
            host = "localhost";
            statusServerBuilder = NettyServerBuilder.forPort(0);
            streamingServerBuilder = useDifferentPorts ? NettyServerBuilder.forPort(0) : statusServerBuilder;
        }

        statusServerBuilder.addService(new StatusService());
        streamingServerBuilder.addService(new StreamSubscribeService());

        statusServer = statusServerBuilder.build().start();
        streamingServer = streamingServerBuilder != statusServerBuilder ? streamingServerBuilder.build().start() : statusServer;

        statusPort = statusServer.getPort();
        streamingPort = streamingServer.getPort();

        started = true;
        return this;
    }

    public void setLatency(int latency) {
        if (latency < 0) {
            throw new IllegalArgumentException("Latency must be a non-negative integer");
        }

        this.latency.set(latency);
    }

    public BlockNodeProperties toClientProperties() {
        validateState(started, "BlockNodeSimulator has not been started");
        var properties = new BlockNodeProperties();
        properties.setHost(host);
        properties.setPriority(priority);
        properties.setStatusPort(statusPort);
        properties.setStreamingPort(streamingPort);
        return properties;
    }

    public BlockNodeSimulator withBlocks(List<BlockGenerator.BlockRecord> blocks) {
        validateArg(!CollectionUtils.isEmpty(blocks), "blocks can't be empty");
        this.blocks = new ArrayList<>(blocks);
        firstBlockNumber =
                blocks.getFirst().block().getBlockItems(0).getBlockHeader().getNumber();
        lastBlockNumber =
                blocks.getLast().block().getBlockItems(0).getBlockHeader().getNumber();
        return this;
    }

    public BlockNodeSimulator withBlockInterval(Duration interval) {
        this.interval = interval;
        return this;
    }

    public BlockNodeSimulator withChunksPerBlock(int chunksPerBlock) {
        validateArg(chunksPerBlock > 0, "chunksPerBlock must be greater than 0");
        this.chunksPerBlock = chunksPerBlock;
        return this;
    }

    public BlockNodeSimulator withHostPrefix(String hostPrefix) {
        this.hostPrefix = hostPrefix;
        return this;
    }

    public BlockNodeSimulator withHttpChannel() {
        inProcessChannel = false;
        return this;
    }

    public BlockNodeSimulator withInProcessChannel() {
        inProcessChannel = true;
        return this;
    }

    public BlockNodeSimulator withLatency(int latency) {
        this.latency.set(latency);
        return this;
    }

    public BlockNodeSimulator withMissingBlock() {
        this.missingBlock = true;
        return this;
    }

    public BlockNodeSimulator withOutOfOrder() {
        this.outOfOrder = true;
        return this;
    }

    public BlockNodeSimulator withPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public BlockNodeSimulator withSamePort() {
        this.useDifferentPorts = false;
        return this;
    }

    private static void validateArg(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    @SneakyThrows
    private void shutdown(final Server server) {
        server.shutdown();
        server.awaitTermination();
    }

    private static void validateState(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private final class StatusService extends BlockNodeServiceGrpc.BlockNodeServiceImplBase {

        @Override
        public void serverStatus(ServerStatusRequest request, StreamObserver<ServerStatusResponse> responseObserver) {
            var response = ServerStatusResponse.newBuilder()
                    .setFirstAvailableBlock(firstBlockNumber)
                    .setLastAvailableBlock(lastBlockNumber)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    private final class StreamSubscribeService
            extends BlockStreamSubscribeServiceGrpc.BlockStreamSubscribeServiceImplBase {

        @Override
        public void subscribeBlockStream(
                SubscribeStreamRequest request, StreamObserver<SubscribeStreamResponse> responseObserver) {
            new SubscribeStreamContext(request, responseObserver).stream();
        }
    }

    @RequiredArgsConstructor
    private final class SubscribeStreamContext {

        private static final SubscribeStreamResponse SUCCESS = SubscribeStreamResponse.newBuilder()
                .setStatus(SubscribeStreamResponse.Code.SUCCESS)
                .build();

        final SubscribeStreamRequest request;
        final StreamObserver<SubscribeStreamResponse> responseObserver;

        void stream() {
            if (request.getStartBlockNumber() > lastBlockNumber) {
                responseObserver.onNext(SubscribeStreamResponse.newBuilder()
                        .setStatus(SubscribeStreamResponse.Code.INVALID_START_BLOCK_NUMBER)
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // no other sanity checks, add when needed
            boolean isInfiniteStreaming = request.getEndBlockNumber() == -1;
            int start = (int) (request.getStartBlockNumber() - firstBlockNumber);
            long last = isInfiniteStreaming ? blocks.size() : Math.min(blocks.size(), request.getEndBlockNumber() + 1);

            var responseCallObserver = (ServerCallStreamObserver<SubscribeStreamResponse>) responseObserver;
            for (int i = 0; i < last - start; i++) {
                if (responseCallObserver.isCancelled()) {
                    responseObserver.onCompleted();
                    return;
                }

                int index = start + i;
                delayBlock(index, isInfiniteStreaming);

                var block = blocks.get(index).block();
                if (chunksPerBlock == 1) {
                    responseObserver.onNext(blockResponse(block));
                } else {
                    int chunkSize = Math.max(1, block.getBlockItemsCount() / chunksPerBlock);
                    for (int startIndex = 0; startIndex < block.getBlockItemsCount(); startIndex += chunkSize) {
                        int endIndex = Math.min(block.getBlockItemsCount(), startIndex + chunkSize);
                        var chunk = block.getBlockItemsList().subList(startIndex, endIndex);
                        responseObserver.onNext(blockResponse(chunk));
                    }
                }
                responseObserver.onNext(endOfBlock(firstBlockNumber + index));
            }

            responseObserver.onNext(SUCCESS);
            responseObserver.onCompleted();
        }

        private static SubscribeStreamResponse blockResponse(BlockItemSet block) {
            return SubscribeStreamResponse.newBuilder().setBlockItems(block).build();
        }

        private static SubscribeStreamResponse blockResponse(Collection<BlockItem> items) {
            return blockResponse(
                    BlockItemSet.newBuilder().addAllBlockItems(items).build());
        }

        @SneakyThrows
        private void delayBlock(int index, boolean isInfiniteStreaming) {
            if (interval == null) {
                return;
            }

            // default delay when startBlockNumber == endBlockNumber in request (only in async background latency check)
            // or not the first block
            long delay = interval.toMillis() + latency.get();
            if (isInfiniteStreaming && index != 0) {
                // calculate the delay based on the previous streamed block's stats
                var record = blocks.get(index - 1);
                delay += record.readyTime().get() - record.latency().get() - System.currentTimeMillis();
            }

            if (delay > 0) {
                Thread.sleep(delay);
            }

            var record = blocks.get(index);
            record.latency().compareAndExchange(0L, latency.get());
            record.readyTime().compareAndExchange(0L, System.currentTimeMillis());
        }

        private static SubscribeStreamResponse endOfBlock(final long blockNumber) {
            return SubscribeStreamResponse.newBuilder()
                    .setEndOfBlock(BlockEnd.newBuilder().setBlockNumber(blockNumber))
                    .build();
        }
    }
}
