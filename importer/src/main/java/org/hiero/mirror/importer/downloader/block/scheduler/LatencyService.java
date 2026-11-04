// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeFailure;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A latency service to measure a selection of block nodes' streaming latency in background. Each run probes up to
 * backlog + 1 due nodes, the least recently probed or measured first.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public final class LatencyService implements AutoCloseable {

    static final String PROBE_FAILURE_METRIC_NAME = "hiero.mirror.importer.block.probe";

    private static final long MIN_FRESHNESS_RUNS = 3;
    private static final String NOT_AVAILABLE_KIND = "not_available";
    private static final int NOT_AVAILABLE_STALE_THRESHOLD = 3;
    private static final Comparator<Probe> PROBE_COMPARATOR = Comparator.comparingLong(p -> p.lastProbeMillis);

    private final BlockStreamReader blockStreamReader;
    private final CutoverService cutoverService;
    private final LatencyServiceProperties latencyServiceProperties;
    private final MeterRegistry meterRegistry;

    private volatile Set<BlockNode> nodes = Set.of();

    // Only accessed by the scheduled runs, which never overlap
    private final Map<BlockNode, Probe> probes = new HashMap<>();

    @Override
    public void close() {
        nodes = Set.of();
    }

    @Scheduled(fixedDelayString = "#{@latencyServiceProperties.getFrequency().toMillis()}")
    public void schedule() {
        final var current = nodes;
        probes.keySet().retainAll(current);

        final long now = System.currentTimeMillis();
        final var dueProbes = new ArrayList<Probe>(current.size());
        for (final var node : current) {
            final var probe = probes.computeIfAbsent(node, Probe::new);
            if (now >= probe.nextProbeMillis) {
                dueProbes.add(probe);
            }
        }

        dueProbes.sort(PROBE_COMPARATOR);

        int budget = latencyServiceProperties.getBacklog() + 1;
        for (final var probe : dueProbes) {
            if (budget == 0 || Thread.currentThread().isInterrupted()) {
                break;
            }

            // Skip a node removed since the run started, e.g. it's now the node being streamed from
            if (nodes.contains(probe.node) && probe.run()) {
                budget--;
            }
        }
    }

    /**
     * Gets the max age of a latency sample still considered fresh. It covers the number of runs needed to probe every
     * node, with a minimum of 3 runs.
     */
    long getMaxLatencyAgeMillis() {
        final int budget = latencyServiceProperties.getBacklog() + 1;
        final long runs = Math.max(MIN_FRESHNESS_RUNS, (nodes.size() + budget - 1) / budget + 1L);
        return runs * latencyServiceProperties.getFrequency().toMillis();
    }

    /**
     * Sets the block nodes to measure latency for. The probing state of a node which remains is kept.
     *
     * @param nodes - Block nodes
     */
    void setNodes(final Collection<BlockNode> nodes) {
        this.nodes = Set.copyOf(nodes);
    }

    private final class Probe {

        private long backoffMillis;
        private long lastMeasuredBlockNumber = -1;
        private long lastProbeMillis;
        private long nextProbeMillis;
        private final BlockNode node;
        private int notAvailable;

        Probe(final BlockNode node) {
            this.node = node;
            // A node just measured while being streamed from is probed after the others
            lastProbeMillis = node.getLatency().getLastMeasuredMillis();
        }

        /**
         * Probes the node's latency.
         *
         * @return false if the probe was skipped
         */
        boolean run() {
            final long blockNumber = cutoverService.getNextBlockNumber();
            if (blockNumber < 0 || blockNumber == lastMeasuredBlockNumber) {
                // Don't stream what's already streamed, since that'll incorrectly increase the latency
                return false;
            }

            lastProbeMillis = System.currentTimeMillis();
            try {
                if (node.hasBlock(blockNumber)) {
                    streamBlock(blockNumber);
                    onSuccess(blockNumber);
                } else {
                    onNotAvailable();
                }
            } catch (final Exception ex) {
                onFailure(BlockNodeFailure.of(ex), ex);
            }

            return true;
        }

        private void countFailure(final BlockNodeFailure failure) {
            countFailure(failure.name().toLowerCase());
        }

        private void countFailure(final String kind) {
            meterRegistry
                    .counter(PROBE_FAILURE_METRIC_NAME, "block_node", node.getStatusEndpoint(), "kind", kind)
                    .increment();
        }

        private void markStale(final BlockNodeFailure failure, final Throwable cause) {
            if (node.getLatency().markStale()) {
                log.warn(
                        "Marked latency of {} stale after latency probe failed with {}, cause: {}",
                        node,
                        failure,
                        cause.getMessage());
            }
        }

        private boolean measureLatency(final long blockNumber, final BlockStream blockStream) {
            final var blockFile = blockStreamReader.read(blockStream);
            if (blockFile.getIndex() != null && blockFile.getIndex() != blockNumber) {
                log.warn("Streamed block {} instead of block {} from {}", blockFile.getIndex(), blockNumber, node);
                return false;
            }

            node.getLatency().record(Utils.getLatency(blockFile, blockStream));
            return true;
        }

        private void onFailure(final BlockNodeFailure failure, final Exception cause) {
            if (failure == BlockNodeFailure.IGNORED) {
                return;
            }

            final long frequency = latencyServiceProperties.getFrequency().toMillis();
            final long maxBackoff = latencyServiceProperties.getMaxBackoff().toMillis();
            backoffMillis = backoffMillis == 0 ? frequency : Math.min(backoffMillis * 2, maxBackoff);
            nextProbeMillis = lastProbeMillis + backoffMillis;
            log.debug("Latency probe of {} failed with {}", node, failure, cause);
            markStale(failure, cause);
            countFailure(failure);
        }

        private void onNotAvailable() {
            // The node answered, so a later failure starts over from the base backoff
            backoffMillis = 0;
            if (++notAvailable >= NOT_AVAILABLE_STALE_THRESHOLD
                    && node.getLatency().markStale()) {
                log.warn("Marked latency of {} stale after {} latency probes without the block", node, notAvailable);
            }

            countFailure(NOT_AVAILABLE_KIND);
        }

        private void onSuccess(final long blockNumber) {
            if (backoffMillis > 0) {
                log.info("Measured latency of {} again after failed latency probes", node);
            }

            backoffMillis = 0;
            lastMeasuredBlockNumber = blockNumber;
            nextProbeMillis = 0;
            notAvailable = 0;
        }

        private void streamBlock(final long blockNumber) {
            log.debug("Measuring {}'s latency by streaming block {}", node, blockNumber);
            final var measured = new AtomicBoolean();
            node.probeBlock(
                    blockNumber,
                    (blockStream, _) -> {
                        measured.set(measureLatency(blockNumber, blockStream));
                        return true;
                    },
                    latencyServiceProperties.getTimeout());

            if (!measured.get()) {
                // The stream can end gracefully before any block is streamed
                throw new BlockStreamException("%s ended the stream without block %d".formatted(node, blockNumber));
            }
        }
    }
}
