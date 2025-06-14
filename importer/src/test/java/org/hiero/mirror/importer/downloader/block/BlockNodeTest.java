// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.BindableService;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.ServerStatusResponse;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

@ExtendWith(GrpcCleanupExtension.class)
class BlockNodeTest extends BlockNodeTestBase {

    private static final String SERVER = "test1";

    private BlockNodeProperties blockNodeProperties;
    private BlockNode node;
    private StreamProperties streamProperties;

    @BeforeEach
    void setup() {
        blockNodeProperties = new BlockNodeProperties();
        blockNodeProperties.setHost("in-process:" + SERVER);
        streamProperties = new StreamProperties();
        node = new BlockNode(blockNodeProperties, streamProperties);
    }

    @Test
    void hasBlock(Resources resources) {
        // given
        runBlockNodeService(resources, () -> ServerStatusResponse.newBuilder()
                .setFirstAvailableBlock(20)
                .setLastAvailableBlock(100)
                .build());

        // when, then
        assertThat(node.hasBlock(1)).isFalse();
        assertThat(node.hasBlock(20)).isTrue();
        assertThat(node.hasBlock(101)).isFalse();
    }

    @Test
    void hasBlockTimeout(Resources resources) {
        // given
        streamProperties.setStatusTimeout(Duration.ofMillis(1));
        runBlockNodeService(resources, () -> {
            try {
                Thread.sleep(20);
                return ServerStatusResponse.newBuilder()
                        .setFirstAvailableBlock(20)
                        .setLastAvailableBlock(100)
                        .build();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // when, then
        assertThat(node.hasBlock(20)).isFalse();
    }

    @Test
    void isActive() {
        assertThat(node.isActive()).isTrue();
    }

    @Test
    void onError() {
        for (int i = 0; i < 2; i++) {
            node.onError();
            assertThat(node.isActive()).isTrue();
        }

        node.onError();
        assertThat(node.isActive()).isFalse();
    }

    @Test
    void stream(Resources resources) {
        // given
        var responses = List.of(
                subscribeStreamResponse(blockItemSet(blockHead(0))),
                subscribeStreamResponse(blockItemSet()),
                subscribeStreamResponse(blockItemSet(eventHeader(), blockProof())),
                subscribeStreamResponse(blockItemSet(blockHead(1), eventHeader(), blockProof())));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        StepVerifier.create(node.stream(0))
                .assertNext(streamedBlock -> assertStreamedBlock(streamedBlock, 0))
                .assertNext(streamedBlock -> assertStreamedBlock(streamedBlock, 1))
                .expectComplete()
                .verify();
    }

    @Test
    void streamStatusCode(Resources resources) {
        // given
        var responses = List.of(subscribeStreamResponse(SubscribeStreamResponse.Code.READ_STREAM_NOT_AVAILABLE));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        StepVerifier.create(node.stream(0))
                .expectErrorMessage("Received status READ_STREAM_NOT_AVAILABLE from block node")
                .verify();
    }

    @Test
    void streamIncorrectFirstBlockItem(Resources resources) {
        // given
        var responses = List.of(subscribeStreamResponse(blockItemSet(eventHeader(), blockProof())));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        StepVerifier.create(node.stream(0))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BlockStreamException.class)
                        .hasMessageContaining("Incorrect first block item case"))
                .verify();
    }

    @ParameterizedTest(name = "Unexpected {0}")
    @MethodSource("provideUnexpectedNewBlockItem")
    void streamMissingBlockProof(BlockItem.ItemCase itemCase, BlockItem blockItem, Resources resources) {
        // given
        var responses = List.of(
                subscribeStreamResponse(blockItemSet(blockHead(0), eventHeader())),
                subscribeStreamResponse(blockItemSet(blockItem)));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        StepVerifier.create(node.stream(0))
                .expectErrorMessage("Received block items of a new block while the previous block is still pending")
                .verify();
    }

    @Test
    void streamMoreThanOneBlockItemWhenFirstIsRecordFileItem(Resources resources) {
        // given
        var responses = List.of(subscribeStreamResponse(blockItemSet(recordFileItem(), blockHead(0))));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        StepVerifier.create(node.stream(0))
                .expectErrorMessage("The first block item is record file and there are more than one block items")
                .verify();
    }

    @Test
    void streamOnError(Resources resources) {
        // given
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromError(new RuntimeException("oops")));

        // when, then
        StepVerifier.create(node.stream(0))
                .expectError(StatusRuntimeException.class)
                .verify();
    }

    @Test
    void streamRecordFileItemThenBlockItems(Resources resources) {
        // given
        var responses = List.of(
                subscribeStreamResponse(blockItemSet(recordFileItem())),
                subscribeStreamResponse(blockItemSet(blockHead(1), eventHeader(), blockProof())));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        StepVerifier.create(node.stream(0))
                .assertNext(streamedBlock -> assertThat(streamedBlock)
                        .satisfies(
                                s -> assertThat(s.loadStart())
                                        .isGreaterThan(
                                                Instant.now().minusSeconds(10).toEpochMilli()),
                                s -> assertThat(s)
                                        .extracting(
                                                BlockNode.StreamedBlock::blockItems,
                                                InstanceOfAssertFactories.collection(BlockItem.class))
                                        .hasSize(1)
                                        .first()
                                        .returns(BlockItem.ItemCase.RECORD_FILE, BlockItem::getItemCase)))
                .assertNext(streamedBlock -> assertStreamedBlock(streamedBlock, 1))
                .expectComplete()
                .verify();
    }

    @Test
    void streamTooManyBlockItems(Resources resources) {
        // given
        streamProperties.setMaxBlockItems(2);
        var responses = List.of(
                subscribeStreamResponse(blockItemSet(blockHead(1), eventHeader())),
                subscribeStreamResponse(blockItemSet(eventHeader(), blockProof())));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        StepVerifier.create(node.stream(0))
                .expectErrorMessage("Too many block items in a pending block: received 4, limit 2")
                .verify();
    }

    @Test
    void stringify() {
        var expected = String.format("BlockNode(%s)", blockNodeProperties.getEndpoint());
        assertThat(node.toString()).isEqualTo(expected);

        blockNodeProperties.setHost("localhost");
        blockNodeProperties.setPort(50000);
        expected = "BlockNode(localhost:50000)";
        assertThat(node.toString()).isEqualTo(expected);
    }

    @Test
    void tryReadmit() {
        // given
        assertThat(node.tryReadmit(false).isActive()).isTrue();

        // when
        for (int i = 0; i < 3; i++) {
            node.onError();
        }

        // then
        assertThat(node.tryReadmit(false).isActive()).isFalse();
        assertThat(node.tryReadmit(true).isActive()).isTrue();

        // when become inactive again
        for (int i = 0; i < 3; i++) {
            node.onError();
        }

        // and readmit delay elapsed
        var future = Instant.now().plus(streamProperties.getReadmitDelay()).plusSeconds(1);
        try (var mockedInstant = Mockito.mockStatic(Instant.class)) {
            mockedInstant.when(Instant::now).thenReturn(future);
            assertThat(node.isActive()).isFalse();
            assertThat(node.tryReadmit(false).isActive()).isTrue();
        }
    }

    private static Stream<Arguments> provideUnexpectedNewBlockItem() {
        return Stream.of(
                Arguments.of(BlockItem.ItemCase.BLOCK_HEADER, blockHead(1)),
                Arguments.of(BlockItem.ItemCase.RECORD_FILE, recordFileItem()));
    }

    private void assertStreamedBlock(BlockNode.StreamedBlock streamedBlock, long number) {
        var blockItems = streamedBlock.blockItems();
        assertThat(blockItems.getFirst())
                .returns(BlockItem.ItemCase.BLOCK_HEADER, BlockItem::getItemCase)
                .extracting(BlockItem::getBlockHeader)
                .returns(number, BlockHeader::getNumber);
        assertThat(blockItems.getLast()).returns(BlockItem.ItemCase.BLOCK_PROOF, BlockItem::getItemCase);
        assertThat(streamedBlock.loadStart())
                .isGreaterThan(Instant.now().minusSeconds(10).toEpochMilli());
    }

    private void runBlockNodeService(Resources resources, Supplier<ServerStatusResponse> responseProvider) {
        var service = new BlockNodeServiceGrpc.BlockNodeServiceImplBase() {
            @Override
            public void serverStatus(
                    ServerStatusRequest request, StreamObserver<ServerStatusResponse> responseObserver) {
                responseObserver.onNext(responseProvider.get());
                responseObserver.onCompleted();
            }
        };
        startServer(resources, service);
    }

    private void runBlockStreamSubscribeService(Resources resources, ResponsesOrError responsesOrError) {
        var service = new BlockStreamSubscribeServiceGrpc.BlockStreamSubscribeServiceImplBase() {
            @Override
            public void subscribeBlockStream(
                    SubscribeStreamRequest request, StreamObserver<SubscribeStreamResponse> responseObserver) {
                if (!responsesOrError.responses().isEmpty()) {
                    responsesOrError.responses().forEach(responseObserver::onNext);
                    responseObserver.onCompleted();
                } else {
                    responseObserver.onError(responsesOrError.error());
                }
            }
        };
        startServer(resources, service);
    }

    @SneakyThrows
    private void startServer(Resources resources, BindableService service) {
        var server = InProcessServerBuilder.forName(SERVER)
                .addService(service)
                .directExecutor()
                .build()
                .start();
        resources.register(server);
    }
}
