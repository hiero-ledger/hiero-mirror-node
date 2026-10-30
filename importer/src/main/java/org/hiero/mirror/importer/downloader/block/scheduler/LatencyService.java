// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNode.BlockStatus;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A latency service to measure a selection of block nodes' streaming latency in background. Each run streams a block
 * from up to backlog + 1 nodes with the oldest latency samples, and checks the block status of the other nodes.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public final class LatencyService implements AutoCloseable {

    static final String PROBE_FAILURE_METRIC_NAME = "hiero.mirror.importer.block.latency.probe.failure";

    private static final int MAX_BACKOFF_EXPONENT = 3;
    private static final long MIN_FRESHNESS_RUNS = 3;
    private static final int NOT_AVAILABLE_STALE_THRESHOLD = 3;
    private static final int STREAM_FAILURE_STALE_THRESHOLD = 2;

    private final BlockStreamReader blockStreamReader;
    private final CutoverService cutoverService;
    private final LatencyServiceProperties latencyServiceProperties;
    private final MeterRegistry meterRegistry;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final ThreadPoolExecutor executor = createExecutor();

    private volatile List<Task> tasks = List.of();

    @Override
    public void close() {
        cancelAll();
        getExecutor().shutdownNow();
    }

    @Scheduled(fixedDelayString = "#{@latencyServiceProperties.getFrequency().toMillis()}")
    public void schedule() {
        final var current = tasks;
        if (current.isEmpty()) {
            return;
        }

        final long nextBlockNumber = cutoverService.getNextBlockNumber();
        if (nextBlockNumber < 0) {
            return;
        }

        // Snapshot the sample times so a concurrent measurement can't break the sort
        final long now = System.currentTimeMillis();
        final var due = new ArrayList<DueTask>(current.size());
        for (final var task : current) {
            if (task.isDue(now)) {
                due.add(new DueTask(task, task.node.getLatency().getLastMeasuredMillis()));
            }
        }

        due.sort(Comparator.comparingLong(DueTask::lastMeasuredMillis));

        int budget = latencyServiceProperties.getBacklog() + 1;
        for (final var dueTask : due) {
            final var task = dueTask.task();
            if (budget > 0 && task.lastMeasuredBlockNumber != nextBlockNumber && task.submitProbe()) {
                budget--;
            } else {
                task.checkStatus(nextBlockNumber);
            }
        }
    }

    void cancelAll() {
        final var current = tasks;
        tasks = List.of();
        current.forEach(Task::cancel);
    }

    /**
     * Gets the max age of a latency sample still considered fresh. It covers the number of runs needed to probe every
     * node that isn't backing off, with a minimum of 3 runs.
     */
    long getMaxLatencyAgeMillis() {
        int count = 0;
        for (final var task : tasks) {
            if (!task.isBackingOff()) {
                count++;
            }
        }

        final int budget = latencyServiceProperties.getBacklog() + 1;
        final long runs = Math.max(MIN_FRESHNESS_RUNS, (count + budget - 1) / budget + 1L);
        return runs * latencyServiceProperties.getFrequency().toMillis();
    }

    /**
     * Set the block nodes to asynchronously measure latency for. The probing state of a node which remains is kept.
     *
     * @param nodes - Block nodes
     */
    void setNodes(final Collection<BlockNode> nodes) {
        final var existing = new IdentityHashMap<BlockNode, Task>();
        for (final var task : tasks) {
            existing.put(task.node, task);
        }

        final var next = new ArrayList<Task>(nodes.size());
        for (final var node : nodes) {
            final var task = existing.remove(node);
            next.add(task != null ? task : new Task(node));
        }

        tasks = List.copyOf(next);
        existing.values().forEach(Task::cancel);
    }

    private ThreadPoolExecutor createExecutor() {
        // a single-thread threadpool executor with a blocking queue to holding the backlog, to schedule 1 running
        // + backlog pending scheduled tasks
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(latencyServiceProperties.getBacklog()));
    }

    private enum Outcome {
        AVAILABLE,
        MEASURED,
        NOT_AVAILABLE,
        STREAM_FAILURE,
        STREAM_TIMEOUT,
        UNREACHABLE;

        boolean isFailure() {
            return this != AVAILABLE && this != MEASURED;
        }
    }

    private record DueTask(Task task, long lastMeasuredMillis) {}

    @RequiredArgsConstructor
    private final class Task {

        private final BlockNode node;

        private volatile boolean busy;
        private volatile boolean cancelled;
        private int consecutiveFailures;
        private volatile @Nullable Future<?> future;
        private volatile long lastMeasuredBlockNumber = -1;
        private volatile long nextProbeMillis;
        private int notAvailable;
        private boolean stale;
        private int streamFailures;

        void cancel() {
            cancelled = true;
            final var current = future;
            if (current != null) {
                current.cancel(true);
            }
        }

        void checkStatus(final long blockNumber) {
            busy = true;
            node.getBlockStatus(blockNumber).thenAccept(status -> onOutcome(toOutcome(status), null));
        }

        synchronized boolean isBackingOff() {
            return consecutiveFailures > 1;
        }

        boolean isDue(final long now) {
            return !busy && !cancelled && now >= nextProbeMillis;
        }

        boolean submitProbe() {
            busy = true;
            try {
                future = getExecutor().submit(this::probe);
                return true;
            } catch (final RejectedExecutionException _) {
                busy = false;
                return false;
            }
        }

        private void backoff() {
            final int exponent = Math.min(consecutiveFailures++, MAX_BACKOFF_EXPONENT);
            final long delay = latencyServiceProperties.getFrequency().toMillis() << exponent;
            nextProbeMillis = System.currentTimeMillis() + delay;
        }

        private void markStale(final Outcome outcome, @Nullable final Throwable cause) {
            stale = true;
            if (node.getLatency().markStale()) {
                log.warn("Marked latency of {} stale after latency probe failed with {}", node, outcome, cause);
            }
        }

        private boolean measureLatency(final BlockStream blockStream, final String blockNode) {
            final var blockFile = blockStreamReader.read(blockStream);
            node.getLatency().record(Utils.getLatency(blockFile, blockStream));
            return true;
        }

        private synchronized void onOutcome(final Outcome outcome, @Nullable final Throwable cause) {
            busy = false;
            if (cancelled) {
                return;
            }

            switch (outcome) {
                case AVAILABLE -> {
                    notAvailable = 0;
                    // A status check can't prove a failed stream works again, only a measurement can
                    if (streamFailures == 0) {
                        consecutiveFailures = 0;
                        nextProbeMillis = 0;
                    }
                }
                case MEASURED -> {
                    if (stale) {
                        log.info("Measured latency of {} again after it was marked stale", node);
                        stale = false;
                    }

                    consecutiveFailures = 0;
                    nextProbeMillis = 0;
                    notAvailable = 0;
                    streamFailures = 0;
                }
                case NOT_AVAILABLE -> {
                    if (++notAvailable >= NOT_AVAILABLE_STALE_THRESHOLD) {
                        markStale(outcome, cause);
                    }
                }
                case STREAM_FAILURE -> {
                    backoff();
                    if (++streamFailures >= STREAM_FAILURE_STALE_THRESHOLD) {
                        markStale(outcome, cause);
                    }
                }
                case STREAM_TIMEOUT -> {
                    backoff();
                    streamFailures++;
                    markStale(outcome, cause);
                }
                case UNREACHABLE -> {
                    backoff();
                    markStale(outcome, cause);
                }
            }

            if (outcome.isFailure()) {
                log.debug("Latency probe of {} failed with {}", node, outcome, cause);
                meterRegistry
                        .counter(
                                PROBE_FAILURE_METRIC_NAME,
                                "block_node",
                                node.getEndpoint(),
                                "kind",
                                outcome.name().toLowerCase(Locale.ROOT))
                        .increment();
            }
        }

        private void probe() {
            try {
                final long nextBlockNumber = cutoverService.getNextBlockNumber();
                if (nextBlockNumber < 0 || nextBlockNumber == lastMeasuredBlockNumber) {
                    // Don't stream what's already streamed, since that'll incorrectly increase the latency
                    return;
                }

                final var status = node.getBlockStatus(nextBlockNumber).get();
                if (status != BlockStatus.AVAILABLE) {
                    onOutcome(toOutcome(status), null);
                    return;
                }

                log.debug("Measuring {}'s latency by streaming block {}", node, nextBlockNumber);
                node.probeBlock(nextBlockNumber, this::measureLatency, latencyServiceProperties.getTimeout());
                lastMeasuredBlockNumber = nextBlockNumber;
                onOutcome(Outcome.MEASURED, null);
            } catch (final InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (final BlockStreamException ex) {
                // The node reported it has the block but failed to actually stream it
                final var outcome =
                        ex.getCause() instanceof TimeoutException ? Outcome.STREAM_TIMEOUT : Outcome.STREAM_FAILURE;
                onOutcome(outcome, ex);
            } catch (final ExecutionException | RuntimeException ex) {
                log.warn("Failed to measure latency of {}", node, ex);
            } finally {
                busy = false;
            }
        }

        private static Outcome toOutcome(final BlockStatus status) {
            return switch (status) {
                case AVAILABLE -> Outcome.AVAILABLE;
                case NOT_AVAILABLE -> Outcome.NOT_AVAILABLE;
                case UNREACHABLE -> Outcome.UNREACHABLE;
            };
        }
    }
}
