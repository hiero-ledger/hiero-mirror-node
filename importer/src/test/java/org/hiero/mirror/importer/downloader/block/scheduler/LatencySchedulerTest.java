// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.importer.downloader.block.scheduler.AbstractLatencyAwareScheduler.LATENCY_METRIC_NAME;
import static org.mockito.Mockito.doReturn;

import com.asarkar.grpc.test.Resources;
import java.time.Duration;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.junit.jupiter.api.Test;
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

        // seed server-01's latency
        getLatencyCandidate().getLatency().record(300);

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

    @Test
    void getNodeSortsOldLatencyLast(final Resources resources) throws InterruptedException {
        // given
        final var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withAllBlocks()), runBlockNodeService(0, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        doReturn(50L).when(latencyService).getMaxLatencyAgeMillis();
        scheduler = createScheduler();
        final var scheduled = scheduler.getNode(0);
        assertScheduledBlockNode(scheduled, 0L, blockNodeProperties.getFirst());

        // when the candidate's lower latency sample becomes older than the max age
        getLatencyCandidate().getLatency().record(10);
        Thread.sleep(100);
        setLatency(scheduled, 500);

        // then
        assertScheduledBlockNode(scheduler.getNode(1), 1L, blockNodeProperties.getFirst());
    }

    @Test
    void latencyMetric(final Resources resources) {
        // given
        final var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withAllBlocks()), runBlockNodeService(0, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler();

        // when
        final var scheduled = scheduler.getNode(0);
        setLatency(scheduled, 500);

        // then
        final var endpoint = scheduled.blockNode().getEndpoint();
        assertThat(meterRegistry.find(LATENCY_METRIC_NAME).gauges()).hasSize(2);
        assertThat(meterRegistry
                        .get(LATENCY_METRIC_NAME)
                        .tag("block_node", endpoint)
                        .gauge()
                        .value())
                .isEqualTo(500.0);
        assertThat(meterRegistry
                        .get(LATENCY_METRIC_NAME)
                        .tag("block_node", getLatencyCandidate().getEndpoint())
                        .gauge()
                        .value())
                .isNaN();

        // when
        scheduler.close();

        // then
        assertThat(meterRegistry.find(LATENCY_METRIC_NAME).gauges()).isEmpty();
    }

    @Test
    void shouldRescheduleIgnoresStaleCandidateLatency(Resources resources) throws InterruptedException {
        // given two nodes in the same group
        final var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withAllBlocks()), runBlockNodeService(0, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        doReturn(1L).when(latencyService).getMaxLatencyAgeMillis();
        scheduler = createScheduler(Duration.ZERO, Duration.ofMillis(10));
        final var scheduled = scheduler.getNode(0);
        assertScheduledBlockNode(scheduled, 0L, blockNodeProperties.getFirst());

        // when the candidate has a much better latency, but it's stale relative to the probing frequency
        getLatencyCandidate().getLatency().record(10);
        Thread.sleep(20);

        // then no switch
        assertThat(scheduler.shouldReschedule(blockFile(), blockStream())).isFalse();
    }

    @Test
    void shouldRescheduleWhenCandidateLatencyIsFresh(Resources resources) {
        // given two nodes in the same group
        final var blockNodeProperties = List.of(
                runBlockNodeService(0, resources, withAllBlocks()), runBlockNodeService(0, resources, withAllBlocks()));
        doReturn(blockNodeProperties).when(blockNodeDiscoveryService).getBlockNodes();
        scheduler = createScheduler(Duration.ZERO, Duration.ofMillis(10));
        final var scheduled = scheduler.getNode(0);
        assertScheduledBlockNode(scheduled, 0L, blockNodeProperties.getFirst());

        // when the candidate has a much better latency
        getLatencyCandidate().getLatency().record(10);

        // then switch
        assertThat(scheduler.shouldReschedule(blockFile(), blockStream())).isTrue();
    }

    private static BlockFile blockFile() {
        return BlockFile.builder().consensusEnd(0L).build();
    }

    private static BlockStream blockStream() {
        return new BlockStream(List.of(), 500L, null, "test", 0L, 0);
    }

    private Scheduler createScheduler(final Duration minRescheduleInterval, final Duration rescheduleLatencyThreshold) {
        final var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setType(SchedulerType.LATENCY);
        schedulerProperties.setMinRescheduleInterval(minRescheduleInterval);
        schedulerProperties.setRescheduleLatencyThreshold(rescheduleLatencyThreshold);
        return new LatencyScheduler(
                blockNodeDiscoveryService,
                channelBuilderProvider,
                latencyService,
                meterRegistry,
                schedulerProperties,
                streamProperties);
    }

    @Override
    protected Scheduler createScheduler() {
        var schedulerProperties = new SchedulerProperties();
        schedulerProperties.setType(SchedulerType.LATENCY);
        return new LatencyScheduler(
                blockNodeDiscoveryService,
                channelBuilderProvider,
                latencyService,
                meterRegistry,
                schedulerProperties,
                streamProperties);
    }
}
