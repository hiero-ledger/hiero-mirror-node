// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.simulator;

import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.ForwardingServerBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

public final class BlockNodeSimulator implements AutoCloseable {

    private List<BlockItemSet> blocks = Collections.emptyList();
    private int chunksPerBlock = 1;
    private long firstBlockNumber;
    private String host;
    private boolean inProcessChannel = true;
    private long lastBlockNumber;
    private boolean outOfOrder;
    private int port;
    private Server server;
    private boolean started;
    private boolean withMissingBlock;
    private boolean withHashMismatch;
    private boolean withMissingBlockProof;

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

        if (withMissingBlock){
            blocks.remove(blocks.size()-2);
        }

        if (withHashMismatch){
            blocks.remove(blocks.size()-2);
        }

        if (withMissingBlockProof){
            System.out.println(blocks.get(1).getBlockItems(23).getBlockProof().getPreviousBlockRootHash().substring(3));
            System.out.println(blocks.get(1).getBlockItems(23).getBlockProof().getPreviousBlockRootHash().substring(3).size());
            blocks.get(1).getBlockItems(23).getBlockProof().getPreviousBlockRootHash();
            System.out.println(blocks.get(1).getBlockItems(23).getBlockProof().getPreviousBlockRootHash().substring(3));
            System.out.println(blocks.get(1).getBlockItems(23).getBlockProof().getPreviousBlockRootHash().substring(3).size());
        }

        ForwardingServerBuilder<?> serverBuilder;
        if (inProcessChannel) {
            host = RandomStringUtils.secure().nextAlphabetic(8);
            serverBuilder = InProcessServerBuilder.forName(host);
        } else {
            host = "localhost";
            serverBuilder = NettyServerBuilder.forPort(0);
        }
        // only support InProcess channel now, will expand to http channel
        server = serverBuilder
                .addService(new StatusService())
                .addService(new StreamSubscribeService())
                .build()
                .start();
        port = server.getPort();
        started = true;
        return this;
    }

    public BlockNodeProperties toClientProperties() {
        validateState(started, "BlockNodeSimulator has not been started");
        var properties = new BlockNodeProperties();
        properties.setHost(host);
        properties.setPort(port);
        return properties;
    }

    public BlockNodeSimulator withBlocks(List<BlockItemSet> blocks) {
        validateArg(!CollectionUtils.isEmpty(blocks), "blocks can't be empty");
        this.blocks = new ArrayList<>(blocks);
        firstBlockNumber = blocks.getFirst().getBlockItems(0).getBlockHeader().getNumber();
        lastBlockNumber = blocks.getLast().getBlockItems(0).getBlockHeader().getNumber();
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

    public BlockNodeSimulator withOutOrder() {
        this.outOfOrder = true;
        return this;
    }

    public BlockNodeSimulator withMissingBlock() {
        this.withMissingBlock = true;
        return this;
    }

    public BlockNodeSimulator withMissingBlockProof() {
        this.withMissingBlockProof = true;
        return this;
    }

    public BlockNodeSimulator withHashMismatch() {
        this.withHashMismatch = true;
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

            // no other sanity checks,  add when needed
            int index = (int) (request.getStartBlockNumber() - firstBlockNumber);
            for (; index < blocks.size(); index++) {
                var block = blocks.get(index);
                if (chunksPerBlock == 1) {
                    responseObserver.onNext(blockResponse(blocks.get(index)));
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
    }
}
