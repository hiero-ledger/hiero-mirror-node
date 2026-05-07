// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.asarkar.grpc.test.Resources;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.BlockNode;
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
        assertThat(scheduler.getNode(blockNumber(0)))
                .extracting(BlockNode::getProperties)
                .isEqualTo(blockNodeProperties.getFirst());
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
        assertThat(scheduler.getNode(blockNumber(0)))
                .extracting(BlockNode::getProperties)
                .isEqualTo(blockNodeProperties.getFirst());
        assertThat(scheduler.getNode(blockNumber(1)))
                .extracting(BlockNode::getProperties)
                .isEqualTo(blockNodeProperties.get(1));
        assertThat(scheduler.getNode(blockNumber(2)))
                .extracting(BlockNode::getProperties)
                .isEqualTo(blockNodeProperties.get(2));
        assertThat(scheduler.getNode(blockNumber(3)))
                .extracting(BlockNode::getProperties)
                .isEqualTo(blockNodeProperties.getLast());
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
