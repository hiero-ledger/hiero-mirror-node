// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.mockito.Mockito.doReturn;

import com.asarkar.grpc.test.Resources;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class LatencySchedulerTest extends AbstractSchedulerTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 0
            0, 1
            """)
    void getNode(int priorityA, int priorityB, Resources resources) {
        // given
        final var blockNodeProperties = List.of(
                runBlockNodeService(priorityA, resources, withAllBlocks()),
                runBlockNodeService(priorityB, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when
        var scheduled = scheduler.getNode(0);

        // then
        assertScheduledBlockNode(scheduled, 0L, blockNodeProperties.getFirst());

        // when server-00's latency gets updated
        setLatency(scheduled, 500);
        scheduled = scheduler.getNode(1);

        // then
        assertScheduledBlockNode(scheduled, 1L, blockNodeProperties.getLast());

        // when server-01's latency becomes higher
        setLatency(scheduled, 700);
        scheduled = scheduler.getNode(1);

        // then
        assertScheduledBlockNode(scheduled, 1L, blockNodeProperties.getFirst());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 0
            0, 1
            """)
    void getNodeIgnoreNodeWithoutBlock(int priorityA, int priorityB, Resources resources) {
        // given block node A only has block 0 and block node b has all blocks
        final var blockNodeProperties = List.of(
                runBlockNodeService(priorityA, resources, withBlocks(0, 0)),
                runBlockNodeService(priorityB, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when
        var scheduled = scheduler.getNode(1);

        // then
        assertScheduledBlockNode(scheduled, 1L, blockNodeProperties.getLast());

        // when server-01's latency gets updated
        setLatency(scheduled, 500);
        scheduled = scheduler.getNode(2);

        // then
        assertScheduledBlockNode(scheduled, 2L, blockNodeProperties.getLast());
    }

    @Override
    protected Scheduler createScheduler() {
        var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setType(SchedulerType.LATENCY);
        return new LatencyScheduler(
                blockNodeDiscoveryService,
                InProcessManagedChannelBuilderProvider.INSTANCE,
                latencyService,
                meterRegistry,
                schedulerProperties,
                new StreamProperties());
    }
}
