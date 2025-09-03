// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.asarkar.grpc.test.Resources;
import java.util.Collection;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.SchedulerProperties;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.junit.jupiter.api.Test;

final class PriorityAndLatencySchedulerTest extends AbstractSchedulerTest {

    @Test
    void getNode(Resources resources) {
        // given
        var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withBlocks(0, 1)),
                runBlockNodeService(0, resources, withBlocks(0, 1)),
                runBlockNodeService(1, resources, withAllBlocks()),
                runBlockNodeService(1, resources, withAllBlocks()));
        scheduler = createScheduler(blockNodeProperties);

        // when, then
        var node = scheduler.getNode(0);
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.getFirst());

        // when server-00's latency becomes higher then server-01
        setLatency(node, 500);
        node = scheduler.getNode(1);
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.get(1));

        // when requesting a block priority-0 nodes don't have
        node = scheduler.getNode(2);
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.get(2));

        // when server-02's latency becomes higher than server-03
        setLatency(node, 600);
        node = scheduler.getNode(3);

        // then
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.get(3));
    }

    @Test
    void getNodeWhenHigherPriorityNodesMissingBlocks(Resources resources) {
        // given
        var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withBlocks(0, 0)),
                runBlockNodeService(0, resources, withBlocks(1, 1)),
                runBlockNodeService(1, resources, withBlocks(2, 2)),
                runBlockNodeService(1, resources, withAllBlocks()));
        scheduler = createScheduler(blockNodeProperties);

        // when, then
        assertThat(scheduler.getNode(0)).extracting(BlockNode::getProperties).isEqualTo(blockNodeProperties.getFirst());
        assertThat(scheduler.getNode(1)).extracting(BlockNode::getProperties).isEqualTo(blockNodeProperties.get(1));
        assertThat(scheduler.getNode(2)).extracting(BlockNode::getProperties).isEqualTo(blockNodeProperties.get(2));
        assertThat(scheduler.getNode(3)).extracting(BlockNode::getProperties).isEqualTo(blockNodeProperties.getLast());
    }

    @Override
    protected Scheduler createScheduler(Collection<BlockNodeProperties> blockNodeProperties) {
        var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setType(SchedulerType.PRIORITY_THEN_LATENCY);
        return new PriorityAndLatencyScheduler(
                blockNodeProperties,
                latencyService,
                InProcessManagedChannelBuilderProvider.INSTANCE,
                schedulerProperties,
                new StreamProperties());
    }
}
