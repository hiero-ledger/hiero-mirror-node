// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.importer.downloader.block.BlockNodeTestUtils.singleEndpointProperties;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import lombok.SneakyThrows;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.ServerStatusResponse;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({GrpcCleanupExtension.class, MockitoExtension.class})
@NullUnmarked
abstract class AbstractSchedulerTest {

    @Mock
    protected BlockNodeDiscoveryService blockNodeDiscoveryService;

    protected ManagedChannelBuilderProvider channelBuilderProvider = InProcessManagedChannelBuilderProvider.INSTANCE;

    @Mock
    protected LatencyService latencyService;

    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @AutoClose
    protected Scheduler scheduler;

    protected StreamProperties streamProperties;

    private int serverIndex;

    @BeforeEach
    void setup() {
        streamProperties = new StreamProperties();
        streamProperties.setShutdownTimeout(Duration.ofMillis(100));
    }

    @Test
    void closeRemovedNodeOnRefresh(Resources resources) {
        // given a single serving node whose status channel works
        final var nodeA = runBlockNodeService(0, resources, withAllBlocks());
        doReturn(List.of(nodeA)).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();
        final var removed = scheduler.getNode(0).blockNode();
        assertThat(removed.getBlockRange().isEmpty()).isFalse();

        // when nodeA is no longer discovered (replaced by nodeB)
        final var nodeB = runBlockNodeService(0, resources, withAllBlocks());
        doReturn(List.of(nodeB)).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler.getNode(0);

        // then the removed node's channel is shut down so its status request now fails and yields an empty range
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(
                        () -> assertThat(removed.getBlockRange().isEmpty()).isTrue());
    }

    @Test
    void noNodeHasBlock(Resources resources) {
        // given
        var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withBlocks(0, 0)),
                runBlockNodeService(0, resources, withBlocks(0, 0)));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when, then
        assertThatThrownBy(() -> scheduler.getNode(1))
                .isInstanceOf(BlockStreamException.class)
                .hasMessageContaining("No block node can provide block 1");
    }

    @Test
    void reusesUnchangedNodesOnRefresh(final Resources resources) {
        // given two discovered nodes
        channelBuilderProvider = spy(InProcessManagedChannelBuilderProvider.INSTANCE);
        final var nodeA = runBlockNodeService(0, resources, withAllBlocks());
        final var nodeB = runBlockNodeService(1, resources, withAllBlocks());
        doReturn(List.of(nodeA, nodeB)).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();
        final var first = scheduler.getNode(0).blockNode();

        // when nodeB is replaced by nodeC while nodeA is unchanged
        final var nodeC = runBlockNodeService(1, resources, withAllBlocks());
        doReturn(List.of(nodeA, nodeC)).when(blockNodeDiscoveryService).getBlockNodes();
        final var second = scheduler.getNode(0).blockNode();

        // then nodeA is reused (same instance, its channel is not rebuilt) and nodeC is newly built
        assertThat(second).isSameAs(first);
        final var endpointA = nodeA.getEndpoints().first();
        final var endpointC = nodeC.getEndpoints().first();
        verify(channelBuilderProvider, times(1))
                .get(endpointA.getHost(), endpointA.getPort(), endpointA.isRequiresTls());
        verify(channelBuilderProvider, times(1))
                .get(endpointC.getHost(), endpointC.getPort(), endpointC.isRequiresTls());
    }

    protected abstract Scheduler createScheduler();

    protected void assertScheduledBlockNode(
            final ScheduledBlockNode scheduled,
            final long expectedBlockNumber,
            final BlockNodeProperties expectedProperties) {
        assertThat(scheduled)
                .returns(expectedBlockNumber, ScheduledBlockNode::nextBlockNumber)
                .extracting(ScheduledBlockNode::blockNode)
                .extracting(BlockNode::getProperties)
                .isEqualTo(expectedProperties);
    }

    @SneakyThrows
    protected BlockNodeProperties runBlockNodeService(
            int priority, Resources resources, ServerStatusResponse response) {
        var service = new BlockNodeServiceGrpc.BlockNodeServiceImplBase() {
            @Override
            public void serverStatus(
                    ServerStatusRequest request, StreamObserver<ServerStatusResponse> responseObserver) {
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };

        var name = String.format("server-%02d", serverIndex++);
        var server =
                InProcessServerBuilder.forName(name).addService(service).build().start();
        resources.register(server);

        final var properties = singleEndpointProperties(name);
        properties.setPriority(priority);
        return properties;
    }

    protected void setLatency(final ScheduledBlockNode scheduled, final long latency) {
        final var node = scheduled.blockNode();
        for (int i = 0; i < 5; i++) {
            node.getLatency().record(latency);
        }
    }

    protected static ServerStatusResponse withAllBlocks() {
        return withBlocks(0, Long.MAX_VALUE);
    }

    protected static ServerStatusResponse withBlocks(long first, long last) {
        return ServerStatusResponse.newBuilder()
                .setFirstAvailableBlock(first)
                .setLastAvailableBlock(last)
                .build();
    }
}
