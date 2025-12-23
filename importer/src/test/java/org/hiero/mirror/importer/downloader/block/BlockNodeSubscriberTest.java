// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.BlockStreamSubscribeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.ServerStatusResponse;
import org.hiero.block.api.protoc.SubscribeStreamRequest;
import org.hiero.block.api.protoc.SubscribeStreamResponse;
import org.hiero.block.api.protoc.SubscribeStreamResponse.Code;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({GrpcCleanupExtension.class, MockitoExtension.class})
final class BlockNodeSubscriberTest extends BlockNodeTestBase {

    private final String[] SERVER_NAMES = {"test1", "test2", "test3"};
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private BlockStreamReader blockStreamReader;

    @Mock
    private BlockStreamVerifier blockStreamVerifier;

    private BlockNodeSubscriber blockNodeSubscriber;
    private CommonDownloaderProperties commonDownloaderProperties;
    private Map<String, Server> servers;
    private Map<String, Integer> statusCalls;
    private Map<String, Integer> streamCalls;

    @BeforeEach
    void setup() {
        final var blockProperties = new BlockProperties();
        commonDownloaderProperties = new CommonDownloaderProperties(new ImporterProperties());
        servers = new HashMap<>();
        statusCalls = new HashMap<>();
        streamCalls = new HashMap<>();
        blockProperties.setNodes(List.of(
                blockNodeProperties(0, SERVER_NAMES[0]),
                blockNodeProperties(0, SERVER_NAMES[1]),
                blockNodeProperties(1, SERVER_NAMES[2])));
        blockNodeSubscriber = new BlockNodeSubscriber(
                blockStreamReader,
                blockStreamVerifier,
                commonDownloaderProperties,
                InProcessManagedChannelBuilderProvider.INSTANCE,
                blockProperties,
                meterRegistry);
    }

    @ParameterizedTest(name = "last block number {0}")
    @CsvSource(
            textBlock =
                    """
                            5, '1,0,0', '1,0,0'
                            6, '1,1,0', '0,1,0'
                            7, '1,1,1', '0,0,1'
                            """)
    void get(long lastBlockNumber, String expectedStatusCalls, String expectedStreamCalls, Resources resources) {
        // given
        doReturn(new BlockFile()).when(blockStreamReader).read(any());
        doReturn(Optional.of(BlockFile.builder().index(lastBlockNumber).build()))
                .when(blockStreamVerifier)
                .getLastBlockFile();
        doNothing().when(blockStreamVerifier).verify(any());
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(6, 6),
                ResponsesOrError.fromResponses(fullBlockResponses(6)));
        startServer(
                SERVER_NAMES[1],
                resources,
                serverStatusResponse(7, 7),
                ResponsesOrError.fromResponses(fullBlockResponses(7)));
        startServer(
                SERVER_NAMES[2],
                resources,
                serverStatusResponse(8, 8),
                ResponsesOrError.fromResponses(fullBlockResponses(8)));

        // when
        blockNodeSubscriber.get();

        // then
        assertCalls(statusCalls, expectedStatusCalls);
        assertCalls(streamCalls, expectedStreamCalls);
        verify(blockStreamReader).read(argThat(blockStream -> {
            assertBlockStream(blockStream, lastBlockNumber + 1);
            return true;
        }));
        verify(blockStreamVerifier).getLastBlockFile();
        verify(blockStreamVerifier).verify(any());
    }

    @Test
    void getFromEarliestAvailableBlockNumber(Resources resources) {
        // given
        commonDownloaderProperties.getImporterProperties().setStartBlockNumber(-1L);
        doReturn(new BlockFile()).when(blockStreamReader).read(any());
        doReturn(Optional.empty()).when(blockStreamVerifier).getLastBlockFile();
        doNothing().when(blockStreamVerifier).verify(any());
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(6, 6),
                ResponsesOrError.fromResponses(fullBlockResponses(6)));

        // when
        blockNodeSubscriber.get();

        // then
        assertCalls(statusCalls, "1,0,0");
        assertCalls(streamCalls, "1,0,0");
        verify(blockStreamReader).read(argThat(blockStream -> {
            assertBlockStream(blockStream, 6);
            return true;
        }));
        verify(blockStreamVerifier).getLastBlockFile();
        verify(blockStreamVerifier).verify(any());
    }

    @Test
    void getFromEarliestAvailableBlockNumberPastEndBlockNumber(Resources resources) {
        // given
        final var importerProperties = commonDownloaderProperties.getImporterProperties();
        importerProperties.setStartBlockNumber(-1L);
        importerProperties.setEndBlockNumber(5L);
        doReturn(Optional.empty()).when(blockStreamVerifier).getLastBlockFile();
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(6, 6),
                ResponsesOrError.fromResponses(fullBlockResponses(6)));

        // when
        blockNodeSubscriber.get();

        // then
        assertCalls(statusCalls, "1,0,0");
        assertCalls(streamCalls, "0,0,0");
        verify(blockStreamReader, never()).read(any());
        verify(blockStreamVerifier).getLastBlockFile();
        verify(blockStreamVerifier, never()).verify(any());
    }

    @Test
    void getWhenBlockNotAvailable(Resources resources) {
        // given
        doReturn(Optional.of(BlockFile.builder().index(20L).build()))
                .when(blockStreamVerifier)
                .getLastBlockFile();
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(6, 6),
                ResponsesOrError.fromResponses(fullBlockResponses(6)));
        startServer(
                SERVER_NAMES[1],
                resources,
                serverStatusResponse(7, 7),
                ResponsesOrError.fromResponses(fullBlockResponses(7)));
        startServer(
                SERVER_NAMES[2],
                resources,
                serverStatusResponse(8, 8),
                ResponsesOrError.fromResponses(fullBlockResponses(8)));

        // when
        assertThatThrownBy(blockNodeSubscriber::get)
                .isInstanceOf(BlockStreamException.class)
                .hasMessage("No block node can provide block 21");

        // then
        assertCalls(statusCalls, "1,1,1");
        assertCalls(streamCalls, "0,0,0");
        verifyNoInteractions(blockStreamReader);
        verify(blockStreamVerifier).getLastBlockFile();
        verify(blockStreamVerifier, never()).verify(any());
    }

    @Test
    void getWhenEndBlockNumber(Resources resources) {
        // given
        commonDownloaderProperties.getImporterProperties().setEndBlockNumber(6L);
        doReturn(new BlockFile()).when(blockStreamReader).read(any());
        doReturn(
                        Optional.of(BlockFile.builder().index(5L).build()),
                        Optional.of(BlockFile.builder().index(6L).build()))
                .when(blockStreamVerifier)
                .getLastBlockFile();
        doNothing().when(blockStreamVerifier).verify(any());
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(1, 100),
                ResponsesOrError.fromResponses(fullBlockResponses(6))
                        .addResponse(subscribeStreamResponse(Code.SUCCESS)));

        // when
        blockNodeSubscriber.get(); // First call ends the stream
        blockNodeSubscriber.get(); // Second call never starts the stream

        // then
        assertCalls(statusCalls, "1,0,0");
        assertCalls(streamCalls, "1,0,0");
        verify(blockStreamReader).read(argThat(blockStream -> {
            assertBlockStream(blockStream, 6L);
            return true;
        }));
        verifyNoMoreInteractions(blockStreamReader);
        verify(blockStreamVerifier, times(2)).getLastBlockFile();
        verify(blockStreamVerifier).verify(any());
        verifyNoMoreInteractions(blockStreamVerifier);
    }

    @Test
    void getWhenFirstNodeFails(Resources resources) {
        // given
        doReturn(Optional.of(BlockFile.builder().index(9L).build()))
                .when(blockStreamVerifier)
                .getLastBlockFile();
        doReturn(new BlockFile()).when(blockStreamReader).read(any());
        doNothing().when(blockStreamVerifier).verify(any());
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(10, 10),
                ResponsesOrError.fromError(new RuntimeException("oops")));
        startServer(
                SERVER_NAMES[1],
                resources,
                serverStatusResponse(10, 11),
                ResponsesOrError.fromResponses(fullBlockResponses(10)).addResponses(fullBlockResponses(11)));
        startServer(
                SERVER_NAMES[2],
                resources,
                serverStatusResponse(10, 10),
                ResponsesOrError.fromResponses(fullBlockResponses(10)));

        // when, then
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(blockNodeSubscriber::get).isInstanceOf(BlockStreamException.class);
        }

        assertCalls(statusCalls, "3,0,0");
        assertCalls(streamCalls, "3,0,0");
        verifyNoInteractions(blockStreamReader);
        verify(blockStreamVerifier, times(3)).getLastBlockFile();
        verify(blockStreamVerifier, never()).verify(any());

        // when get again from the second node
        blockNodeSubscriber.get();

        // then
        assertCalls(statusCalls, "3,1,0");
        assertCalls(streamCalls, "3,1,0");
        verify(blockStreamVerifier, times(4)).getLastBlockFile();
        verify(blockStreamVerifier, times(2)).verify(any());

        var captor = ArgumentCaptor.forClass(BlockStream.class);
        verify(blockStreamReader, times(2)).read(captor.capture());
        var values = captor.getAllValues();
        assertBlockStream(values.getFirst(), 10);
        assertBlockStream(values.get(1), 11);
    }

    @Test
    void getWhenAllFailThenForceReadmit(Resources resources) {
        // given
        doReturn(Optional.of(BlockFile.builder().index(9L).build()))
                .when(blockStreamVerifier)
                .getLastBlockFile();
        doReturn(new BlockFile()).when(blockStreamReader).read(any());
        doNothing().when(blockStreamVerifier).verify(any());
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(10, 10),
                ResponsesOrError.fromError(new RuntimeException("oops")));
        startServer(
                SERVER_NAMES[1],
                resources,
                serverStatusResponse(10, 11),
                ResponsesOrError.fromError(new RuntimeException("oops")));

        // when, then
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(blockNodeSubscriber::get).isInstanceOf(BlockStreamException.class);
        }

        assertCalls(statusCalls, "3,3,0");
        assertCalls(streamCalls, "3,3,0");
        verifyNoInteractions(blockStreamReader);
        verify(blockStreamVerifier, times(6)).getLastBlockFile();
        verify(blockStreamVerifier, never()).verify(any());

        // when servers become healthy however test1 no longer has the next block
        startServer(
                SERVER_NAMES[0],
                resources,
                serverStatusResponse(20, 20),
                ResponsesOrError.fromResponses(fullBlockResponses(20)));
        startServer(
                SERVER_NAMES[1],
                resources,
                serverStatusResponse(10, 11),
                ResponsesOrError.fromResponses(fullBlockResponses(10)).addResponses(fullBlockResponses(11)));
        blockNodeSubscriber.get();

        // then
        assertCalls(statusCalls, "4,4,0");
        assertCalls(streamCalls, "3,4,0");
        verify(blockStreamVerifier, times(7)).getLastBlockFile();
        verify(blockStreamVerifier, times(2)).verify(any());

        var captor = ArgumentCaptor.forClass(BlockStream.class);
        verify(blockStreamReader, times(2)).read(captor.capture());
        var values = captor.getAllValues();
        assertBlockStream(values.getFirst(), 10);
        assertBlockStream(values.get(1), 11);
    }

    private void assertBlockStream(BlockStream actual, long blockNumber) {
        assertThat(actual)
                .returns(null, BlockStream::bytes)
                .returns(BlockFile.getFilename(blockNumber, false), BlockStream::filename)
                .returns(-1L, BlockStream::nodeId)
                .extracting(BlockStream::loadStart, InstanceOfAssertFactories.LONG)
                .isGreaterThan(0L);
    }

    private void assertCalls(Map<String, Integer> calls, String expected) {
        var actual = Arrays.stream(SERVER_NAMES)
                .map(name -> calls.getOrDefault(name, 0))
                .map(Object::toString)
                .collect(Collectors.joining(","));
        assertThat(actual).isEqualTo(expected);
    }

    private BlockNodeProperties blockNodeProperties(int priority, String serverName) {
        var properties = new BlockNodeProperties();
        properties.setHost(serverName);
        properties.setPriority(priority);
        return properties;
    }

    private void recordCall(String name, Map<String, Integer> calls) {
        calls.compute(name, (key, value) -> value == null ? 1 : value + 1);
    }

    private ServerStatusResponse serverStatusResponse(long firstBlockNumber, long lastBlockNumber) {
        return ServerStatusResponse.newBuilder()
                .setFirstAvailableBlock(firstBlockNumber)
                .setLastAvailableBlock(lastBlockNumber)
                .build();
    }

    @SneakyThrows
    private void startServer(
            String name, Resources resources, ServerStatusResponse statusResponse, ResponsesOrError streamResponse) {
        if (servers.containsKey(name)) {
            var server = servers.get(name);
            server.shutdown();
            server.awaitTermination();
        }

        var statusService = new BlockNodeServiceGrpc.BlockNodeServiceImplBase() {
            @Override
            public void serverStatus(
                    ServerStatusRequest request, StreamObserver<ServerStatusResponse> responseObserver) {
                recordCall(name, statusCalls);
                responseObserver.onNext(statusResponse);
                responseObserver.onCompleted();
            }
        };
        var streamService = new BlockStreamSubscribeServiceGrpc.BlockStreamSubscribeServiceImplBase() {
            @Override
            public void subscribeBlockStream(
                    SubscribeStreamRequest request, StreamObserver<SubscribeStreamResponse> responseObserver) {
                recordCall(name, streamCalls);

                if (!streamResponse.getResponses().isEmpty()) {
                    streamResponse.getResponses().forEach(responseObserver::onNext);
                    responseObserver.onCompleted();
                } else {
                    responseObserver.onError(streamResponse.getError());
                }
            }
        };

        var server = InProcessServerBuilder.forName(name)
                .addService(statusService)
                .addService(streamService)
                .directExecutor()
                .build()
                .start();
        resources.register(server);
        servers.put(name, server);
    }
}
