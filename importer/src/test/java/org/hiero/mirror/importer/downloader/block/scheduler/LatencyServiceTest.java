// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.importer.downloader.block.scheduler.LatencyService.PROBE_METRIC_NAME;
import static org.hiero.mirror.importer.downloader.block.scheduler.LatencyService.STATUS_METRIC_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.grpc.Status;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.awaitility.core.ThrowingRunnable;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@NullUnmarked
final class LatencyServiceTest {

    private static final Duration WAIT = Duration.ofSeconds(2);

    private BlockStreamReader blockStreamReader;

    @Mock
    private CutoverService cutoverService;

    @AutoClose
    private LatencyService latencyService;

    private MeterRegistry meterRegistry;
    private LatencyServiceProperties properties;

    @BeforeEach
    void setup() {
        blockStreamReader = mock(BlockStreamReader.class);
        // The stubbed probes put the block number in the filename
        lenient()
                .doAnswer(invocation -> BlockFile.builder()
                        .consensusEnd(0L)
                        .index(Long.parseLong(
                                invocation.<BlockStream>getArgument(0).filename()))
                        .build())
                .when(blockStreamReader)
                .read(any());
        meterRegistry = new SimpleMeterRegistry();
        properties = new LatencyServiceProperties();
        latencyService = new LatencyService(blockStreamReader, cutoverService, properties, meterRegistry);
    }

    @Test
    void close() {
        // given
        final var blockNode = blockNode(0L);
        latencyService.setNodes(List.of(blockNode));

        // when
        latencyService.close();
        latencyService.schedule();

        // then
        verify(blockNode, never()).hasBlock(anyLong());
    }

    @Test
    void schedule() {
        // given
        final var blockNode = blockNode(0L);
        doReturn(0L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        latencyService.schedule();

        // then
        verify(blockNode.getLatency()).record(anyLong());
        verify(blockNode).hasBlock(0L);
        verify(blockNode).probeBlock(eq(0L), any(), any());

        // when scheduled again, then the already measured block isn't probed again
        latencyService.schedule();
        verify(blockNode).hasBlock(anyLong());

        // when next block advances
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.schedule();

        // then
        verify(blockNode).hasBlock(1L);
        verify(blockNode).probeBlock(eq(1L), any(), any());
        assertThat(probeCount("healthy")).isEqualTo(2);
        assertThat(meterRegistry.find(PROBE_METRIC_NAME).counters()).hasSize(1);
        assertThat(statusGauge(blockNode, "healthy")).isOne();
    }

    @Test
    void scheduleLimitsProbesAndPrefersOldestSample() {
        // given more nodes than the probe budget, the last ones with the oldest samples
        properties.setBacklog(1);
        final var nodes = new ArrayList<BlockNode>();
        for (int i = 0; i < 4; i++) {
            nodes.add(blockNode(1000L - i));
        }

        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(nodes);

        // when
        latencyService.schedule();

        // then the two with the oldest samples are probed, and the others aren't called
        verify(nodes.get(3)).probeBlock(eq(1L), any(), any());
        verify(nodes.get(2)).probeBlock(eq(1L), any(), any());
        verify(nodes.get(1), never()).hasBlock(anyLong());
        verify(nodes.get(0), never()).hasBlock(anyLong());

        // when scheduled again, then the measured ones are skipped without using the budget
        latencyService.schedule();
        verify(nodes.get(1)).probeBlock(eq(1L), any(), any());
        verify(nodes.get(0)).probeBlock(eq(1L), any(), any());
    }

    @Test
    void scheduleRotatesNodesWithoutBlock() {
        // given more nodes without the block than the probe budget
        properties.setBacklog(1);
        final var nodes = new ArrayList<BlockNode>();
        for (int i = 0; i < 6; i++) {
            final var node = blockNode(1000L - i);
            doReturn(false).when(node).hasBlock(anyLong());
            nodes.add(node);
        }

        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(nodes);

        // when
        for (int i = 0; i < 3; i++) {
            latencyService.schedule();
        }

        // then every node is probed once, including the ones with the most recent samples
        for (final var node : nodes) {
            verify(node).hasBlock(1L);
        }

        assertThat(probeCount("not_available")).isEqualTo(6);
    }

    @Test
    void scheduleNotAvailableMarksStale() {
        // given
        final var blockNode = blockNode(0L);
        final var latency = blockNode.getLatency();
        doReturn(false).when(blockNode).hasBlock(anyLong());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when, then the node doesn't back off, and the third probe without the block marks it stale
        latencyService.schedule();
        latencyService.schedule();
        verify(latency, never()).markStale();

        latencyService.schedule();
        verify(latency).markStale();
        assertThat(probeCount("not_available")).isEqualTo(3);
        assertThat(statusGauge(blockNode, "not_available")).isOne();
        verify(blockNode, never()).probeBlock(anyLong(), any(), any());
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("failures")
    void scheduleFailureMarksStaleAndBacksOff(final Consumer<BlockNode> stubFailure, final String status) {
        // given
        final var blockNode = blockNode(0L);
        final var latency = blockNode.getLatency();
        stubFailure.accept(blockNode);
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        latencyService.schedule();

        // then
        verify(latency).markStale();
        verify(latency, never()).record(anyLong());
        assertThat(probeCount(status)).isOne();
        assertThat(meterRegistry.find(PROBE_METRIC_NAME).counters()).hasSize(1);
        assertThat(statusGauge(blockNode, status)).isOne();

        // when scheduled again right away, then the node is backing off
        latencyService.schedule();
        verify(blockNode).hasBlock(1L);
    }

    @Test
    void scheduleStreamsWrongBlock() {
        // given the node streams another block than the requested one
        final var blockNode = blockNode(0L);
        final var latency = blockNode.getLatency();
        doReturn(BlockFile.builder().consensusEnd(0L).index(2L).build())
                .when(blockStreamReader)
                .read(any());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        latencyService.schedule();

        // then
        assertThat(probeCount("error")).isOne();
        verify(latency).markStale();
        verify(latency, never()).record(anyLong());
    }

    @Test
    void scheduleIgnoresInterruptedProbe() {
        // given the probe is interrupted by the importer itself
        final var blockNode = blockNode(0L);
        final var latency = blockNode.getLatency();
        doThrow(new BlockStreamException(new InterruptedException()))
                .when(blockNode)
                .probeBlock(anyLong(), any(), any());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        latencyService.schedule();
        latencyService.schedule();

        // then the node doesn't back off
        verify(blockNode, times(2)).probeBlock(anyLong(), any(), any());
        assertThat(meterRegistry.find(PROBE_METRIC_NAME).counters()).isEmpty();
        assertThat(meterRegistry.find(STATUS_METRIC_NAME).gauges()).isEmpty();
        verify(latency, never()).markStale();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void scheduleBacksOffAndResetsOnceReachable(final boolean hasBlock) {
        // given a node that fails twice, answers once, then fails twice again
        properties.setFrequency(Duration.ofMillis(200));
        final var blockNode = blockNode(0L);
        final var callTimes = new CopyOnWriteArrayList<Long>();
        doAnswer(_ -> {
                    final int call = callTimes.size();
                    callTimes.add(System.currentTimeMillis());
                    if (call == 2) {
                        return hasBlock;
                    }

                    throw Status.UNAVAILABLE.asRuntimeException();
                })
                .when(blockNode)
                .hasBlock(anyLong());
        // a new block for each probe so the probe isn't skipped after the successful measurement
        doAnswer(_ -> (long) callTimes.size()).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        scheduleUntil(() -> assertThat(callTimes).hasSize(5));

        // then the delay doubles after the second failure, and resets once the node answers
        assertThat(callTimes.get(1) - callTimes.get(0)).isBetween(190L, 390L);
        assertThat(callTimes.get(2) - callTimes.get(1)).isGreaterThanOrEqualTo(390L);
        assertThat(callTimes.get(3) - callTimes.get(2)).isLessThan(190L);
        assertThat(callTimes.get(4) - callTimes.get(3)).isBetween(190L, 390L);
    }

    @Test
    void scheduleCapsBackoffAtMaxBackoff() {
        // given a node that keeps failing
        properties.setFrequency(Duration.ofMillis(100));
        properties.setMaxBackoff(Duration.ofMillis(200));
        final var blockNode = blockNode(0L);
        final var callTimes = new CopyOnWriteArrayList<Long>();
        doAnswer(_ -> {
                    callTimes.add(System.currentTimeMillis());
                    throw Status.UNAVAILABLE.asRuntimeException();
                })
                .when(blockNode)
                .hasBlock(anyLong());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));

        // when
        scheduleUntil(() -> assertThat(callTimes).hasSizeGreaterThanOrEqualTo(5));

        // then the delay doubles once, then stays at maxBackoff instead of 400ms and 800ms
        assertThat(callTimes.get(1) - callTimes.get(0)).isBetween(90L, 190L);
        assertThat(callTimes.get(2) - callTimes.get(1)).isBetween(190L, 390L);
        assertThat(callTimes.get(3) - callTimes.get(2)).isBetween(190L, 390L);
        assertThat(callTimes.get(4) - callTimes.get(3)).isBetween(190L, 390L);
    }

    @Test
    void scheduleSkipsNodeRemovedDuringRun() {
        // given the first probed node's status call removes the other node
        final var first = blockNode(0L);
        final var removed = blockNode(1L);
        doAnswer(_ -> {
                    latencyService.setNodes(List.of(first));
                    return true;
                })
                .when(first)
                .hasBlock(anyLong());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(first, removed));

        // when
        latencyService.schedule();

        // then
        verify(first).probeBlock(eq(1L), any(), any());
        verify(removed, never()).hasBlock(anyLong());
    }

    @Test
    void scheduleUnreachableNodeDoesNotDelayOthers() {
        // given
        final var unreachable = blockNode(0L);
        final var healthy = blockNode(1L);
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(unreachable).hasBlock(anyLong());
        latencyService.setNodes(List.of(unreachable, healthy));

        // when, then the healthy node streams a block each run, while the unreachable one backs off
        for (long blockNumber = 1; blockNumber <= 3; blockNumber++) {
            doReturn(blockNumber).when(cutoverService).getNextBlockNumber();
            latencyService.schedule();
            verify(healthy).probeBlock(eq(blockNumber), any(), any());
        }

        verify(unreachable).hasBlock(anyLong());
    }

    @Test
    void setNodesKeepsProbingState() {
        // given a node backing off after a failure
        final var blockNode = blockNode(0L);
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(blockNode).hasBlock(anyLong());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(blockNode));
        latencyService.schedule();

        // when
        latencyService.setNodes(List.of(blockNode));
        latencyService.schedule();

        // then
        verify(blockNode).hasBlock(anyLong());

        // when the node is removed and added back
        latencyService.setNodes(List.of());
        latencyService.schedule();
        latencyService.setNodes(List.of(blockNode));
        latencyService.schedule();

        // then its probing state starts over
        verify(blockNode, times(2)).hasBlock(anyLong());
    }

    @Test
    void statusMetric() {
        // given
        final var healthy = blockNode(0L);
        final var unreachable = blockNode(1L);
        doThrow(Status.UNAVAILABLE.asRuntimeException()).when(unreachable).hasBlock(anyLong());
        doReturn(-1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(healthy, unreachable));

        // when there's no block to probe yet
        latencyService.schedule();

        // then
        assertThat(meterRegistry.find(STATUS_METRIC_NAME).gauges()).isEmpty();

        // when
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.schedule();

        // then each node has one gauge for its status
        assertThat(meterRegistry.find(STATUS_METRIC_NAME).gauges()).hasSize(2);
        assertThat(statusGauge(healthy, "healthy")).isOne();
        assertThat(statusGauge(unreachable, "unreachable")).isOne();

        // when the status changes
        doReturn(false).when(healthy).hasBlock(anyLong());
        doReturn(2L).when(cutoverService).getNextBlockNumber();
        latencyService.schedule();

        // then
        assertThat(meterRegistry.find(STATUS_METRIC_NAME).gauges()).hasSize(2);
        assertThat(statusGauge(healthy, "not_available")).isOne();
        assertThat(meterRegistry
                        .find(STATUS_METRIC_NAME)
                        .tags("block_node", healthy.getSubscribeStreamEndpoint(), "status", "healthy")
                        .gauges())
                .isEmpty();

        // when a node is no longer probed, e.g. it's now the node being streamed from
        latencyService.setNodes(List.of(unreachable));
        latencyService.schedule();

        // then
        assertThat(meterRegistry.find(STATUS_METRIC_NAME).gauges()).hasSize(1);
        assertThat(statusGauge(unreachable, "unreachable")).isOne();

        // when
        latencyService.close();
        latencyService.schedule();

        // then
        assertThat(meterRegistry.find(STATUS_METRIC_NAME).gauges()).isEmpty();
    }

    @Test
    void statusMetricPublishedWhenProbeEnds() {
        // given the second node's probe captures the first node's status gauges
        final var first = blockNode(0L);
        final var second = blockNode(1L);
        final var firstGauges = new AtomicInteger(-1);
        doAnswer(_ -> {
                    firstGauges.set(meterRegistry
                            .find(STATUS_METRIC_NAME)
                            .tags("block_node", first.getSubscribeStreamEndpoint(), "status", "healthy")
                            .gauges()
                            .size());
                    return true;
                })
                .when(second)
                .hasBlock(anyLong());
        doReturn(1L).when(cutoverService).getNextBlockNumber();
        latencyService.setNodes(List.of(first, second));

        // when
        latencyService.schedule();

        // then the first node's status is published before the run ends
        assertThat(firstGauges).hasValue(1);
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

    private static Stream<Arguments> failures() {
        return Stream.of(
                failure(
                        "unreachable",
                        n -> doThrow(Status.UNAVAILABLE.asRuntimeException())
                                .when(n)
                                .hasBlock(anyLong())),
                failure(
                        "rejected",
                        n -> doThrow(Status.PERMISSION_DENIED.asRuntimeException())
                                .when(n)
                                .hasBlock(anyLong())),
                failure(
                        "error",
                        n -> doThrow(Status.INTERNAL.asRuntimeException())
                                .when(n)
                                .hasBlock(anyLong())),
                failure(
                        "timeout",
                        n -> doThrow(new BlockStreamException(new TimeoutException()))
                                .when(n)
                                .probeBlock(anyLong(), any(), any())),
                failure(
                        "error",
                        n -> doThrow(new BlockStreamException("stream failed"))
                                .when(n)
                                .probeBlock(anyLong(), any(), any())),
                // The stream ends gracefully without the block
                failure("error", n -> doNothing().when(n).probeBlock(anyLong(), any(), any())));
    }

    private static Arguments failure(final String status, final Consumer<BlockNode> stubFailure) {
        return Arguments.of(stubFailure, status);
    }

    private BlockNode blockNode(final long lastMeasuredMillis) {
        final var blockNode = mock(BlockNode.class);
        final var latency = mock(Latency.class);
        lenient().doReturn(lastMeasuredMillis).when(latency).getLastMeasuredMillis();
        lenient().doReturn(latency).when(blockNode).getLatency();
        lenient().doReturn("localhost:" + lastMeasuredMillis).when(blockNode).getSubscribeStreamEndpoint();
        lenient().doReturn(true).when(blockNode).hasBlock(anyLong());
        lenient()
                .doAnswer(invocation -> {
                    final long blockNumber = invocation.getArgument(0);
                    final BiFunction<BlockStream, String, Boolean> onBlockStream = invocation.getArgument(1);
                    onBlockStream.apply(
                            new BlockStream(
                                    List.of(), System.currentTimeMillis(), null, String.valueOf(blockNumber), 0),
                            "test");
                    return null;
                })
                .when(blockNode)
                .probeBlock(anyLong(), any(), any());
        return blockNode;
    }

    private double probeCount(final String status) {
        double count = 0;
        for (final var counter :
                meterRegistry.find(PROBE_METRIC_NAME).tag("status", status).counters()) {
            count += counter.count();
        }

        return count;
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

    private double statusGauge(final BlockNode blockNode, final String status) {
        return meterRegistry
                .get(STATUS_METRIC_NAME)
                .tags("block_node", blockNode.getSubscribeStreamEndpoint(), "status", status)
                .gauge()
                .value();
    }
}
