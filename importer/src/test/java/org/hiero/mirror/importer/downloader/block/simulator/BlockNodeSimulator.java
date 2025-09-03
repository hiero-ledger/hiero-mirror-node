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
import java.util.concurrent.atomic.AtomicInteger;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.hiero.block.api.protoc.BlockItemSet;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.ServerStatusResponse;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.springframework.util.CollectionUtils;

@CustomLog
public final class BlockNodeSimulator implements AutoCloseable {

    private List<BlockGenerator.BlockRecord> blocks = Collections.emptyList();
    private int chunksPerBlock = 1;
    private long firstBlockNumber;
    private String host;
    private boolean inProcessChannel = true;
    private Duration interval;
    private long lastBlockNumber;
    private final AtomicInteger latency = new AtomicInteger();
    private boolean missingBlock;
    private boolean outOfOrder;

    @Getter
    private int port;

    private int priority;
    private Server server;
    private boolean started;

    @Override
    @SneakyThrows
    public void close() {
        if (!started) {
            return;
        }

        server.shutdown();
        server.awaitTermination();
        started = false;
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

        ForwardingServerBuilder<?> serverBuilder;
        if (inProcessChannel) {
            host = RandomStringUtils.secure().nextAlphabetic(8);
            serverBuilder = InProcessServerBuilder.forName(host);
        } else {
            host = "localhost";
            serverBuilder = NettyServerBuilder.forPort(0);
        }

        server = serverBuilder
                .addService(new StatusService())
                .addService(new StreamSubscribeService())
                .build()
                .start();
        port = server.getPort();
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
        properties.setPort(port);
        properties.setPriority(priority);
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

    public BlockNodeSimulator withHttpChannel() {
        inProcessChannel = false;
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

    private static void validateArg(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
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
    }
}
