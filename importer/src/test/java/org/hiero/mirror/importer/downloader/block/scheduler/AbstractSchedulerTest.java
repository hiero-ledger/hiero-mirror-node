// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.doReturn;

import com.asarkar.grpc.test.GrpcCleanupExtension;
import com.asarkar.grpc.test.Resources;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import lombok.SneakyThrows;
import org.hiero.block.api.protoc.BlockNodeServiceGrpc;
import org.hiero.block.api.protoc.ServerStatusRequest;
import org.hiero.block.api.protoc.ServerStatusResponse;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({GrpcCleanupExtension.class, MockitoExtension.class})
@NullUnmarked
abstract class AbstractSchedulerTest {

    @Mock
    protected BlockNodeDiscoveryService blockNodeDiscoveryService;

    @Mock
    protected LatencyService latencyService;

    protected MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @AutoClose
    protected Scheduler scheduler;

    private int serverIndex;

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

        var properties = new BlockNodeProperties();
        properties.setHost(name);
        properties.setPriority(priority);
        return properties;
    }

    protected void setLatency(final ScheduledBlockNode scheduled, final long latency) {
        final var node = scheduled.blockNode();
        for (int i = 0; i < 5; i++) {
            node.recordLatency(latency);
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
