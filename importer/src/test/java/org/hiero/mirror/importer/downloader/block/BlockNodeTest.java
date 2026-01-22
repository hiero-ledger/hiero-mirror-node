// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.importer.downloader.block.BlockNode.ERROR_METRIC_NAME;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import com.google.common.collect.Range;
import com.hedera.hapi.block.stream.output.protoc.BlockHeader;
import com.hedera.hapi.block.stream.protoc.BlockItem;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.StatusException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.BlockingClientCall;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ObjectAssert;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.ServerStatusResponse;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

@ExtendWith(GrpcCleanupExtension.class)
final class BlockNodeTest extends BlockNodeTestBase {

    private static final Consumer<BlockStream> IGNORE = b -> {};
    private static final Consumer<BlockingClientCall<?, ?>> NOOP_GRPC_BUFFER_DISPOSER = grpcCall -> {};
    private static final String SERVER = "test1";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private CommonDownloaderProperties commonDownloaderProperties;
    private BlockNodeProperties blockNodeProperties;
    private BlockNode node;
    private StreamProperties streamProperties;

    private static Stream<Arguments> provideUnexpectedNewBlockItem() {
        return Stream.of(
                Arguments.of(BlockItem.ItemCase.BLOCK_HEADER, blockHead(1)),
                Arguments.of(BlockItem.ItemCase.RECORD_FILE, recordFileItem()));
    }

    @BeforeEach
    void setup() {
        commonDownloaderProperties = new CommonDownloaderProperties(new ImporterProperties());
        commonDownloaderProperties.init();
        commonDownloaderProperties.setTimeout(TIMEOUT);
        blockNodeProperties = new BlockNodeProperties();
        blockNodeProperties.setHost(SERVER);
        streamProperties = new StreamProperties();
        node = new BlockNode(
                InProcessManagedChannelBuilderProvider.INSTANCE,
                NOOP_GRPC_BUFFER_DISPOSER,
                blockNodeProperties,
                streamProperties,
                meterRegistry);
    }

    @AfterEach
    void cleanup() {
        node.close();
    }

    @Test
    void compareTo() {
        var first = new BlockNode(
                InProcessManagedChannelBuilderProvider.INSTANCE,
                NOOP_GRPC_BUFFER_DISPOSER,
                blockNodeProperties("localhost", 100, 0),
                streamProperties,
                meterRegistry);
        var second = new BlockNode(
                InProcessManagedChannelBuilderProvider.INSTANCE,
                NOOP_GRPC_BUFFER_DISPOSER,
                blockNodeProperties("localhost", 101, 0),
                streamProperties,
                meterRegistry);
        var third = new BlockNode(
                InProcessManagedChannelBuilderProvider.INSTANCE,
                NOOP_GRPC_BUFFER_DISPOSER,
                blockNodeProperties("peer", 99, 0),
                streamProperties,
                meterRegistry);
        var forth = new BlockNode(
                InProcessManagedChannelBuilderProvider.INSTANCE,
                NOOP_GRPC_BUFFER_DISPOSER,
                blockNodeProperties("localhost", 50, 1),
                streamProperties,
                meterRegistry);
        var all = Stream.of(forth, third, second, first).sorted().toList();
        assertThat(all).containsExactly(first, second, third, forth);
    }

    @Test
    void getBlockRange(Resources resources) {
        // given
        runBlockNodeService(resources, () -> ServerStatusResponse.newBuilder()
                .setFirstAvailableBlock(20)
                .setLastAvailableBlock(100)
                .build());

        // when, then
        assertThat(node.getBlockRange()).isEqualTo(Range.closed(20L, 100L));
    }

    @Test
    void getBlockRangeFromEmptyBlockNode(Resources resources) {
        // given
        runBlockNodeService(resources, () -> ServerStatusResponse.newBuilder()
                .setFirstAvailableBlock(-1)
                .setLastAvailableBlock(-1)
                .build());

        // when, then
        assertThat(node.getBlockRange().isEmpty()).isTrue();
    }

    @Test
    void getBlockRangeTimeout(Resources resources) {
        // given
        streamProperties.setResponseTimeout(Duration.ofMillis(1));
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
        assertThat(node.getBlockRange().isEmpty()).isTrue();
    }

    @Test
    void isActive() {
        assertThat(node.isActive()).isTrue();
    }

    @Test
    void onError(Resources resources) {
        // given
        assertThat(node.isActive()).isTrue();
        var server = runBlockStreamSubscribeService(
                resources,
                ResponsesOrError.fromResponse(subscribeStreamResponse(SubscribeStreamResponse.Code.NOT_AVAILABLE)));

        // when fails twice in a row, the node should still be active
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                    .isInstanceOf(BlockStreamException.class)
                    .hasMessageContaining("Received status NOT_AVAILABLE from block node");
            assertThat(node.isActive()).isTrue();
        }

        // when stream succeeds, the node is active and the error count is reset
        stopServer(server);
        server = runBlockStreamSubscribeService(
                resources,
                ResponsesOrError.fromResponse(subscribeStreamResponse(SubscribeStreamResponse.Code.SUCCESS)));
        node.streamBlocks(0, commonDownloaderProperties, IGNORE);
        assertThat(node.isActive()).isTrue();

        // when fails three times in a row
        stopServer(server);
        runBlockStreamSubscribeService(
                resources,
                ResponsesOrError.fromResponse(subscribeStreamResponse(SubscribeStreamResponse.Code.NOT_AVAILABLE)));
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                    .isInstanceOf(BlockStreamException.class)
                    .hasMessageContaining("Received status NOT_AVAILABLE from block node");
            boolean expected = i < 2;
            assertThat(node.isActive()).isEqualTo(expected);
        }
    }

    @Test
    void stream(Resources resources) {
        // given
        var responses = List.of(
                subscribeStreamResponse(blockItemSet(blockHead(0))),
                subscribeStreamResponse(blockItemSet()),
                subscribeStreamResponse(blockItemSet(eventHeader(), blockProof())),
                subscribeStreamResponse(0),
                subscribeStreamResponse(blockItemSet(blockHead(1), eventHeader(), blockProof())),
                subscribeStreamResponse(1));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when
        var streamed = new ArrayList<BlockStream>();
        node.streamBlocks(0, commonDownloaderProperties, streamed::add);

        // then
        assertThat(streamed)
                .hasSize(2)
                .satisfies(
                        blocks -> assertBlockStream(blocks.getFirst(), 0),
                        blocks -> assertBlockStream(blocks.getLast(), 1));
    }

    @Test
    void streamStatusCode(Resources resources) {
        // given
        var responses = List.of(subscribeStreamResponse(SubscribeStreamResponse.Code.NOT_AVAILABLE));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Received status NOT_AVAILABLE from block node");
    }

    @Test
    void streamIncorrectFirstBlockItem(Resources resources) {
        // given
        var responses = List.of(subscribeStreamResponse(blockItemSet(eventHeader(), blockProof())));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Incorrect first block item case EVENT_HEADER");
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
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Received block items of a new block while the previous block is still pending");
    }

    @Test
    void streamMoreThanOneBlockItemWhenFirstIsRecordFileItem(Resources resources) {
        // given
        var responses = List.of(subscribeStreamResponse(blockItemSet(recordFileItem(), blockHead(0))));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("The first block item is record file and there are more than one block items");
    }

    @Test
    void streamOnError(Resources resources) {
        // given
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromError(new RuntimeException("oops")));

        // when, then
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(StatusException.class);
    }

    @Test
    void streamRecordFileItemThenBlockItems(Resources resources) {
        // given
        var responses = List.of(
                subscribeStreamResponse(blockItemSet(recordFileItem())),
                subscribeStreamResponse(1),
                subscribeStreamResponse(blockItemSet(blockHead(2), eventHeader(), blockProof())),
                subscribeStreamResponse(2),
                subscribeStreamResponse(SubscribeStreamResponse.Code.SUCCESS));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        var streamed = new ArrayList<BlockStream>();
        node.streamBlocks(0, commonDownloaderProperties, streamed::add);

        // then
        assertThat(streamed)
                .hasSize(2)
                .satisfies(
                        blocks -> assertRecordItem(blocks.getFirst()),
                        blocks -> assertBlockStream(blocks.getLast(), 2));
    }

    @Test
    void streamTimeout(Resources resources) {
        // given
        var latch = new CountDownLatch(1);
        runBlockStreamSubscribeService(resources, responseObserver -> {
            try {
                latch.await();
                responseObserver.onNext(subscribeStreamResponse(SubscribeStreamResponse.Code.SUCCESS));
                responseObserver.onCompleted();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // when, then
        commonDownloaderProperties.setTimeout(Duration.ofMillis(100));
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        latch.countDown();
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
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("Too many block items in a pending block: received 4, limit 2");
    }

    @Test
    void stringify() {
        var expected = String.format("BlockNode(%s)", blockNodeProperties.getStatusEndpoint());
        assertThat(node.toString()).isEqualTo(expected);

        blockNodeProperties.setHost("localhost");
        blockNodeProperties.setStatusPort(50000);
        expected = "BlockNode(localhost:50000)";
        assertThat(node.toString()).isEqualTo(expected);
    }

    @Test
    void differentPortsCreatesSeparateChannels() {
        // given
        var provider = Mockito.spy(InProcessManagedChannelBuilderProvider.INSTANCE);
        var properties = new BlockNodeProperties();
        properties.setHost(SERVER);
        properties.setStatusPort(40840);
        properties.setStreamingPort(40841);

        // when
        var blockNode = new BlockNode(provider, NOOP_GRPC_BUFFER_DISPOSER, properties, streamProperties, meterRegistry);

        // then
        Mockito.verify(provider, Mockito.times(1)).get(SERVER, 40840);
        Mockito.verify(provider, Mockito.times(1)).get(SERVER, 40841);
        blockNode.close();
    }

    @Test
    void samePortsReusesSingleChannel() {
        // given
        var provider = Mockito.spy(InProcessManagedChannelBuilderProvider.INSTANCE);
        var properties = new BlockNodeProperties();
        properties.setHost(SERVER);
        properties.setStatusPort(40840);
        properties.setStreamingPort(40840);

        // when
        var blockNode = new BlockNode(provider, NOOP_GRPC_BUFFER_DISPOSER, properties, streamProperties, meterRegistry);

        // then
        Mockito.verify(provider, Mockito.times(1)).get(SERVER, 40840);
        blockNode.close();
    }

    @Test
    void tryReadmit(Resources resources) {
        // given
        assertThat(node.tryReadmit(false).isActive()).isTrue();
        runBlockStreamSubscribeService(
                resources,
                ResponsesOrError.fromResponse(subscribeStreamResponse(SubscribeStreamResponse.Code.NOT_AVAILABLE)));

        // when
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                    .isInstanceOf(BlockStreamException.class)
                    .hasMessageContaining("Received status NOT_AVAILABLE from block node");
        }

        // then
        assertThat(node.tryReadmit(false).isActive()).isFalse();
        assertThat(node.tryReadmit(true).isActive()).isTrue();

        // when become inactive again
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                    .isInstanceOf(BlockStreamException.class)
                    .hasMessageContaining("Received status NOT_AVAILABLE from block node");
        }

        // and readmit delay elapsed
        var future = Instant.now().plus(streamProperties.getReadmitDelay()).plusSeconds(1);
        try (var mockedInstant = Mockito.mockStatic(Instant.class)) {
            mockedInstant.when(Instant::now).thenReturn(future);
            assertThat(node.isActive()).isFalse();
            assertThat(node.tryReadmit(false).isActive()).isTrue();
        }
    }

    @Test
    void streamBlocksSuccessDoesNotIncreaseErrorMetric(Resources resources) {
        // given
        var responses = List.of(
                subscribeStreamResponse(blockItemSet(blockHead(0))),
                subscribeStreamResponse(blockItemSet()),
                subscribeStreamResponse(blockItemSet(eventHeader(), blockProof())),
                subscribeStreamResponse(0),
                subscribeStreamResponse(blockItemSet(blockHead(1), eventHeader(), blockProof())),
                subscribeStreamResponse(1));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when
        node.streamBlocks(0, commonDownloaderProperties, IGNORE);

        // then
        assertThat(meterRegistry.find(ERROR_METRIC_NAME).counter().count()).isEqualTo(0);
    }

    @Test
    void streamBlocksIncrementErrorMetricOnException(Resources resources) {
        // given
        var responses = List.of(subscribeStreamResponse(blockItemSet(recordFileItem(), blockHead(0))));
        runBlockStreamSubscribeService(resources, ResponsesOrError.fromResponses(responses));

        // when, then
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class);
        assertThatThrownBy(() -> node.streamBlocks(0, commonDownloaderProperties, IGNORE))
                .isInstanceOf(BlockStreamException.class);
        assertThat(meterRegistry.find(ERROR_METRIC_NAME).counter().count()).isEqualTo(2);
    }

    private void assertRecordItem(BlockStream blockStream) {
        assertBlockStreamCommon(blockStream)
                .returns(null, BlockStream::filename)
                .extracting(BlockStream::blockItems, InstanceOfAssertFactories.collection(BlockItem.class))
                .hasSize(1)
                .first()
                .returns(BlockItem.ItemCase.RECORD_FILE, BlockItem::getItemCase);
    }

    private BlockNodeProperties blockNodeProperties(String host, int port, int priority) {
        var properties = new BlockNodeProperties();
        properties.setHost(host);
        properties.setStatusPort(port);
        properties.setStreamingPort(port);
        properties.setPriority(priority);
        return properties;
    }

    private void assertBlockStream(BlockStream blockStream, long number) {
        assertBlockStreamCommon(blockStream)
                .returns(BlockFile.getFilename(number, false), BlockStream::filename)
                .extracting(BlockStream::blockItems)
                .satisfies(
                        x -> assertThat(x.getFirst())
                                .returns(BlockItem.ItemCase.BLOCK_HEADER, BlockItem::getItemCase)
                                .extracting(BlockItem::getBlockHeader)
                                .returns(number, BlockHeader::getNumber),
                        x -> assertThat(x.getLast()).returns(BlockItem.ItemCase.BLOCK_PROOF, BlockItem::getItemCase));
    }

    private ObjectAssert<BlockStream> assertBlockStreamCommon(BlockStream blockStream) {
        return assertThat(blockStream)
                .satisfies(b -> assertThat(b.loadStart())
                        .isGreaterThan(Instant.now().minusSeconds(10).toEpochMilli()))
                .returns(-1L, BlockStream::nodeId);
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

    private Server runBlockStreamSubscribeService(
            Resources resources, Consumer<StreamObserver<SubscribeStreamResponse>> responseProvider) {
        var service = new BlockStreamSubscribeServiceGrpc.BlockStreamSubscribeServiceImplBase() {
            @Override
            public void subscribeBlockStream(
                    SubscribeStreamRequest request, StreamObserver<SubscribeStreamResponse> responseObserver) {
                responseProvider.accept(responseObserver);
            }
        };
        return startServer(resources, service);
    }

    private Server runBlockStreamSubscribeService(Resources resources, ResponsesOrError responsesOrError) {
        return runBlockStreamSubscribeService(resources, responseObserver -> {
            if (!responsesOrError.getResponses().isEmpty()) {
                responsesOrError.getResponses().forEach(responseObserver::onNext);
                responseObserver.onCompleted();
            } else {
                responseObserver.onError(responsesOrError.getError());
            }
        });
    }

    @SneakyThrows
    private Server startServer(Resources resources, BindableService service) {
        var server = InProcessServerBuilder.forName(SERVER)
                .addService(service)
                .build()
                .start();
        resources.register(server);
        return server;
    }

    @SneakyThrows
    private void stopServer(Server server) {
        server.shutdown();
        server.awaitTermination();
    }
}
