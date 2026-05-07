// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.mockito.Mockito.doReturn;

import com.asarkar.grpc.test.Resources;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.junit.jupiter.api.Test;

final class PriorityAndLatencySchedulerTest extends AbstractSchedulerTest {

    @Test
    void getNode(Resources resources) {
        // given
        final var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withBlocks(0, 1)),
                runBlockNodeService(0, resources, withBlocks(0, 1)),
                runBlockNodeService(1, resources, withAllBlocks()),
                runBlockNodeService(1, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when, then
        var scheduled = scheduler.getNode(0);
        assertScheduledBlockNode(scheduled, 0L, blockNodeProperties.getFirst());

        // when server-00's latency becomes higher then server-01
        setLatency(scheduled, 500);
        scheduled = scheduler.getNode(1);
        assertScheduledBlockNode(scheduled, 1L, blockNodeProperties.get(1));

        // when requesting a block priority-0 nodes don't have
        scheduled = scheduler.getNode(2);
        assertScheduledBlockNode(scheduled, 2L, blockNodeProperties.get(2));

        // when server-02's latency becomes higher than server-03
        setLatency(scheduled, 600);
        scheduled = scheduler.getNode(3);

        // then
        assertScheduledBlockNode(scheduled, 3L, blockNodeProperties.get(3));
    }

    @Test
    void getNodeWhenHigherPriorityNodesMissingBlocks(Resources resources) {
        // given
        var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withBlocks(0, 0)),
                runBlockNodeService(0, resources, withBlocks(1, 1)),
                runBlockNodeService(1, resources, withBlocks(2, 2)),
                runBlockNodeService(1, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when, then
        assertScheduledBlockNode(scheduler.getNode(0), 0L, blockNodeProperties.getFirst());
        assertScheduledBlockNode(scheduler.getNode(1), 1L, blockNodeProperties.get(1));
        assertScheduledBlockNode(scheduler.getNode(2), 2L, blockNodeProperties.get(2));
        assertScheduledBlockNode(scheduler.getNode(3), 3L, blockNodeProperties.getLast());
    }

    @Override
    protected Scheduler createScheduler() {
        var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setType(SchedulerType.PRIORITY_THEN_LATENCY);
        return new PriorityAndLatencyScheduler(
                blockNodeDiscoveryService,
                InProcessManagedChannelBuilderProvider.INSTANCE,
                latencyService,
                meterRegistry,
                schedulerProperties,
                new StreamProperties());
    }
}
