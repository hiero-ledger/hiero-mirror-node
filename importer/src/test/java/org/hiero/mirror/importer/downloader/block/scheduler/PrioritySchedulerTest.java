// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.mockito.Mockito.doReturn;

import com.asarkar.grpc.test.Resources;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.junit.jupiter.api.Test;

final class PrioritySchedulerTest extends AbstractSchedulerTest {

    @Test
    void getNode(Resources resources) {
        // given
        var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withAllBlocks()),
                runBlockNodeService(0, resources, withAllBlocks()),
                runBlockNodeService(1, resources, withAllBlocks()),
                runBlockNodeService(1, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when, then
        assertScheduledBlockNode(scheduler.getNode(0), 0L, blockNodeProperties.getFirst());
    }

    @Test
    void getNodeIgnoreNodeWithoutBlock(Resources resources) {
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
        return new PriorityScheduler(
                blockNodeDiscoveryService,
                InProcessManagedChannelBuilderProvider.INSTANCE,
                meterRegistry,
                new StreamProperties());
    }
}
