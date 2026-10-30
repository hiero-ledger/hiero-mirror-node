// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.importer.downloader.block.scheduler.LatencyService.PROBE_FAILURE_METRIC_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.core.ThrowingRunnable;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNode.BlockStatus;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@NullUnmarked
final class LatencyServiceTest {

    private static final Duration WAIT = Duration.ofSeconds(2);

    @Mock
    private CutoverService cutoverService;

    @AutoClose
    private LatencyService latencyService;

    private MeterRegistry meterRegistry;
    private LatencyServiceProperties properties;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new LatencyServiceProperties();
        latencyService = new LatencyService(mock(BlockStreamReader.class), cutoverService, properties, meterRegistry);
    }

    @Test
    void schedule() {
        // given
        final var blockNode = blockNode(BlockStatus.AVAILABLE);
        doReturn(0L).when(cutoverService).getNextBlockNumber();

        // when
        latencyService.setNodes(List.of(blockNode));
        latencyService.schedule();

        // then
        await().atMost(WAIT).untilAsserted(() -> verify(blockNode).probeBlock(anyLong(), any(), any()));
        verify(blockNode).getBlockStatus(0L);

        // when schedule again, then the already measured block isn't streamed, only the status is checked
        await().atMost(WAIT).untilAsserted(() -> {
            latencyService.schedule();
            verify(blockNode, times(2)).getBlockStatus(0L);
        });
        verify(blockNode).probeBlock(anyLong(), any(), any());

        // when next block advances
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.schedule();

        // then
        await().atMost(WAIT).untilAsserted(() -> verify(blockNode, times(2)).probeBlock(anyLong(), any(), any()));
        verify(blockNode).getBlockStatus(1L);
    }

    @Test
    void scheduleLimitsStreamsAndPrefersOldestSample() {
        // given more nodes than the stream budget, the last one with the oldest sample
        properties.setBacklog(1);
        final var nodes = new ArrayList<BlockNode>();
        for (int i = 0; i < 4; i++) {
            nodes.add(blockNode(BlockStatus.AVAILABLE, 1000L - i));
        }

        doReturn(1L).when(cutoverService).getNextBlockNumber();

        // when
        latencyService.setNodes(nodes);
        latencyService.schedule();

        // then the two with the oldest samples stream a block, and the others only check the block status
        for (final var node : nodes) {
            await().atMost(WAIT).untilAsserted(() -> verify(node).getBlockStatus(1L));
        }

        await().atMost(WAIT).untilAsserted(() -> {
            verify(nodes.get(3)).probeBlock(anyLong(), any(), any());
            verify(nodes.get(2)).probeBlock(anyLong(), any(), any());
        });
        verify(nodes.get(1), never()).probeBlock(anyLong(), any(), any());
        verify(nodes.get(0), never()).probeBlock(anyLong(), any(), any());
    }

    @Test
    void scheduleNotAvailableMarksStale() {
        // given
        properties.setFrequency(Duration.ofMillis(10));
        final var blockNode = blockNode(BlockStatus.NOT_AVAILABLE);
        final var latency = blockNode.getLatency();
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when, then
        for (int i = 1; i <= 3; i++) {
            final int count = i;
            latencyService.schedule();
            await().atMost(WAIT)
                    .untilAsserted(() -> verify(blockNode, times(count)).getBlockStatus(1L));
            await().atMost(WAIT)
                    .untilAsserted(
                            () -> assertThat(probeFailures("not_available")).isEqualTo(count));
            verify(latency, times(count < 3 ? 0 : 1)).markStale();
        }

        verify(blockNode, never()).probeBlock(anyLong(), any(), any());
    }

    @Test
    void scheduleStreamFailureMarksStale() {
        // given the node claims to have the block but fails to stream it
        properties.setFrequency(Duration.ofMillis(200));
        final var blockNode = blockNode(BlockStatus.AVAILABLE);
        final var latency = blockNode.getLatency();
        doThrow(new BlockStreamException("stream failed")).when(blockNode).probeBlock(anyLong(), any(), any());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when, then
        latencyService.schedule();
        await().atMost(WAIT)
                .untilAsserted(() -> assertThat(probeFailures("stream_failure")).isOne());
        verify(latency, never()).markStale();

        // when, then the second failure marks it stale
        scheduleUntil(() -> assertThat(probeFailures("stream_failure")).isEqualTo(2));
        verify(latency).markStale();
        verify(blockNode, times(2)).probeBlock(anyLong(), any(), any());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            UNREACHABLE, unreachable
            AVAILABLE, stream_timeout
            """)
    void scheduleMarksStaleAfterOneFailure(final BlockStatus status, final String kind) {
        // given
        final var blockNode = blockNode(status);
        final var latency = blockNode.getLatency();
        lenient()
                .doThrow(new BlockStreamException(new TimeoutException()))
                .when(blockNode)
                .probeBlock(anyLong(), any(), any());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        latencyService.schedule();

        // then
        await().atMost(WAIT).untilAsserted(() -> verify(latency).markStale());
        assertThat(probeFailures(kind)).isOne();

        // when scheduled again right away, then the node is backing off
        latencyService.schedule();
        verify(blockNode).getBlockStatus(1L);
    }

    @Test
    void scheduleBacksOffAndResetsAfterSuccess() {
        // given a node that fails twice, succeeds once, then fails twice again
        properties.setFrequency(Duration.ofMillis(200));
        final var blockNode = blockNode(BlockStatus.UNREACHABLE);
        final var statuses = List.of(
                BlockStatus.UNREACHABLE,
                BlockStatus.UNREACHABLE,
                BlockStatus.AVAILABLE,
                BlockStatus.UNREACHABLE,
                BlockStatus.UNREACHABLE);
        final var callTimes = new CopyOnWriteArrayList<Long>();
        doAnswer(_ -> {
                    final var status = statuses.get(Math.min(callTimes.size(), statuses.size() - 1));
                    callTimes.add(System.currentTimeMillis());
                    return CompletableFuture.completedFuture(status);
                })
                .when(blockNode)
                .getBlockStatus(anyLong());
        // a new block for each probe so the status check isn't skipped after the successful measurement
        doAnswer(_ -> (long) callTimes.size()).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        scheduleUntil(() -> assertThat(callTimes).hasSize(statuses.size()));

        // then the delay doubles after the second failure, and resets after the success
        assertThat(callTimes.get(1) - callTimes.get(0)).isBetween(190L, 390L);
        assertThat(callTimes.get(2) - callTimes.get(1)).isGreaterThanOrEqualTo(390L);
        assertThat(callTimes.get(3) - callTimes.get(2)).isLessThan(190L);
        assertThat(callTimes.get(4) - callTimes.get(3)).isBetween(190L, 390L);
    }

    @Test
    void scheduleStatusCheckKeepsStreamBackoff() {
        // given a node backing off after its stream timed out twice
        properties.setBacklog(1);
        properties.setFrequency(Duration.ofMillis(10));
        final var hanging = blockNode(BlockStatus.AVAILABLE, 1000L);
        doThrow(new BlockStreamException(new TimeoutException())).when(hanging).probeBlock(anyLong(), any(), any());
        final var blockNumber = new AtomicLong();
        doAnswer(_ -> blockNumber.incrementAndGet()).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(hanging));
        scheduleUntil(() -> assertThat(probeFailures("stream_timeout")).isEqualTo(2));

        // when nodes with older samples take the stream budget, so the hanging node only gets a status check
        final var nodes = new ArrayList<BlockNode>();
        nodes.add(hanging);
        for (int i = 0; i < 4; i++) {
            nodes.add(blockNode(BlockStatus.AVAILABLE));
        }

        latencyService.setNodes(nodes);
        scheduleUntil(() -> verify(hanging, times(3)).getBlockStatus(anyLong()));

        // then it's still backing off, so the freshness window only counts the other 4 nodes
        verify(hanging, times(2)).probeBlock(anyLong(), any(), any());
        assertThat(latencyService.getMaxLatencyAgeMillis())
                .isEqualTo(3 * properties.getFrequency().toMillis());
    }

    @Test
    void scheduleUnreachableNodeDoesNotDelayOthers() {
        // given
        final var unreachable = blockNode(BlockStatus.UNREACHABLE, 0L);
        final var healthy = blockNode(BlockStatus.AVAILABLE, 1L);
        latencyService.setNodes(List.of(unreachable, healthy));

        // when, then the healthy node streams a block each run, while the unreachable one backs off
        for (int blockNumber = 1; blockNumber <= 3; blockNumber++) {
            final int count = blockNumber;
            doReturn((long) blockNumber).when(cutoverService).getNextBlockNumber();
            scheduleUntil(() -> verify(healthy, times(count)).probeBlock(anyLong(), any(), any()));
        }

        verify(unreachable).getBlockStatus(anyLong());
        verify(unreachable, never()).probeBlock(anyLong(), any(), any());
    }

    @Test
    void setNodesKeepsProbingState() {
        // given a node backing off after a failure
        final var blockNode = blockNode(BlockStatus.UNREACHABLE);
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));
        latencyService.schedule();
        await().atMost(WAIT)
                .untilAsserted(() -> assertThat(probeFailures("unreachable")).isOne());

        // when
        latencyService.setNodes(List.of(blockNode));
        latencyService.schedule();

        // then
        verify(blockNode).getBlockStatus(anyLong());
    }

    @Test
    void getMaxLatencyAgeMillis() {
        // given
        final long frequency = properties.getFrequency().toMillis();

        // when, then at least 3 runs
        assertThat(latencyService.getMaxLatencyAgeMillis()).isEqualTo(3 * frequency);
        latencyService.setNodes(List.of(mock(BlockNode.class), mock(BlockNode.class)));
        assertThat(latencyService.getMaxLatencyAgeMillis()).isEqualTo(3 * frequency);

        // when, then 10 nodes take 3 runs with a budget of 4, plus 1
        final var nodes = new ArrayList<BlockNode>();
        for (int i = 0; i < 10; i++) {
            nodes.add(mock(BlockNode.class));
        }

        latencyService.setNodes(nodes);
        assertThat(latencyService.getMaxLatencyAgeMillis()).isEqualTo(4 * frequency);
    }

    private BlockNode blockNode(final BlockStatus status) {
        return blockNode(status, 0L);
    }

    private BlockNode blockNode(final BlockStatus status, final long lastMeasuredMillis) {
        final var blockNode = mock(BlockNode.class);
        final var latency = mock(Latency.class);
        lenient().doReturn(lastMeasuredMillis).when(latency).getLastMeasuredMillis();
        lenient().doReturn(latency).when(blockNode).getLatency();
        lenient().doReturn("localhost:" + lastMeasuredMillis).when(blockNode).getEndpoint();
        lenient()
                .doAnswer(_ -> CompletableFuture.completedFuture(status))
                .when(blockNode)
                .getBlockStatus(anyLong());
        return blockNode;
    }

    /**
     * Runs the latency service every 10ms until the assertion passes.
     */
    private void scheduleUntil(final ThrowingRunnable assertion) {
        await().atMost(WAIT).pollInterval(Duration.ofMillis(10)).untilAsserted(() -> {
            latencyService.schedule();
            assertion.run();
        });
    }

    private double probeFailures(final String kind) {
        final var counter =
                meterRegistry.find(PROBE_FAILURE_METRIC_NAME).tag("kind", kind).counter();
        return counter == null ? 0 : counter.count();
    }
}
