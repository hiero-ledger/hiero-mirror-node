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
import org.hiero.block.api.protoc.BlockRange;
import org.hiero.block.api.protoc.ServerStatusDetailResponse;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.hiero.mirror.importer.exception.NoBlockNodeAvailableException;
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
        assertThat(removed.getBlockOrEarliest(0)).contains(0L);

        // when nodeA is no longer discovered (replaced by nodeB)
        final var nodeB = runBlockNodeService(0, resources, withAllBlocks());
        doReturn(List.of(nodeB)).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler.getNode(0);

        // then the removed node's channel is shut down so its status request now fails and yields no block
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(removed.getBlockOrEarliest(0)).isEmpty());
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
                .isInstanceOf(NoBlockNodeAvailableException.class)
                .hasMessageContaining("No block node can provide block 1");
    }

    @Test
    void nodeWithGap(Resources resources) {
        // given a node which has blocks [0, 5] and [10, 20] but nothing in between
        var blockNodeProperties = runBlockNodeService(0, resources, withRanges(10, 20, 0, 5));
        doReturn(List.of(blockNodeProperties)).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when, then
        assertScheduledBlockNode(scheduler.getNode(3), 3, blockNodeProperties);
        assertScheduledBlockNode(scheduler.getNode(12), 12, blockNodeProperties);
        assertScheduledBlockNode(scheduler.getNode(Scheduler.EARLIEST_AVAILABLE_BLOCK_NUMBER), 0, blockNodeProperties);
        assertThatThrownBy(() -> scheduler.getNode(7))
                .isInstanceOf(NoBlockNodeAvailableException.class)
                .hasMessageContaining("No block node can provide block 7");
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
            int priority, Resources resources, ServerStatusDetailResponse response) {
        var service = new BlockNodeServiceGrpc.BlockNodeServiceImplBase() {
            @Override
            public void serverStatusDetail(
                    ServerStatusRequest request, StreamObserver<ServerStatusDetailResponse> responseObserver) {
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

    protected static ServerStatusDetailResponse withAllBlocks() {
        return withBlocks(0, Long.MAX_VALUE);
    }

    protected static ServerStatusDetailResponse withBlocks(long first, long last) {
        return withRanges(first, last);
    }

    /**
     * Builds a server status detail response from pairs of inclusive range bounds.
     *
     * @param bounds Flattened range bounds, e.g. {@code withRanges(0, 5, 10, 20)} for blocks [0, 5] and [10, 20]
     */
    protected static ServerStatusDetailResponse withRanges(long... bounds) {
        if (bounds.length % 2 != 0) {
            throw new IllegalArgumentException("bounds must have an even number of elements");
        }

        var builder = ServerStatusDetailResponse.newBuilder();
        for (int i = 0; i < bounds.length; i += 2) {
            builder.addAvailableRanges(
                    BlockRange.newBuilder().setRangeStart(bounds[i]).setRangeEnd(bounds[i + 1]));
        }

        return builder.build();
    }
}
