// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.asarkar.grpc.test.Resources;
import java.util.Collection;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.InProcessManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.SchedulerProperties;
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
        var blockNodeProperties = List.of(
                runBlockNodeService(priorityA, resources, withAllBlocks()),
                runBlockNodeService(priorityB, resources, withAllBlocks()));
        scheduler = createScheduler(blockNodeProperties);

        // when
        var node = scheduler.getNode(blockNumber(0));

        // then
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.getFirst());

        // when server-00's latency gets updated
        setLatency(node, 500);
        node = scheduler.getNode(blockNumber(1));

        // then
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.getLast());

        // when server-01's latency becomes higher
        setLatency(node, 700);
        node = scheduler.getNode(blockNumber(1));

        // then
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.getFirst());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 0
            0, 1
            """)
    void getNodeIgnoreNodeWithoutBlock(int priorityA, int priorityB, Resources resources) {
        // given block node A only has block 0 and block node b has all blocks
        var blockNodeProperties = List.of(
                runBlockNodeService(priorityA, resources, withBlocks(0, 0)),
                runBlockNodeService(priorityB, resources, withAllBlocks()));
        scheduler = createScheduler(blockNodeProperties);

        // when
        var node = scheduler.getNode(blockNumber(1));

        // then
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.getLast());

        // when server-01's latency gets updated
        node.recordLatency(500);
        node = scheduler.getNode(blockNumber(2));

        // then
        assertThat(node.getProperties()).isEqualTo(blockNodeProperties.getLast());
    }

    @Override
    protected Scheduler createScheduler(Collection<BlockNodeProperties> blockNodeProperties) {
        var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setType(SchedulerType.LATENCY);
        return new LatencyScheduler(
                blockNodeProperties,
                latencyService,
                InProcessManagedChannelBuilderProvider.INSTANCE,
                meterRegistry,
                schedulerProperties,
                new StreamProperties());
    }
}
