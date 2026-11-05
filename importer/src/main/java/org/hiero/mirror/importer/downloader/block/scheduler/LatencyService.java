// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeStatus;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A latency service to measure a selection of block nodes' streaming latency in background. Each run probes up to
 * backlog + 1 due nodes, the least recently probed or measured first.
 */
@CustomLog
@Named
public final class LatencyService implements AutoCloseable {

    static final String PROBE_METRIC_NAME = "hiero.mirror.importer.block.probe";
    static final String STATUS_METRIC_NAME = "hiero.mirror.importer.block.status";

    private static final String BLOCK_NODE_TAG = "block_node";
    private static final long MIN_FRESHNESS_RUNS = 3;
    private static final int NOT_AVAILABLE_STALE_THRESHOLD = 3;
    private static final Comparator<Probe> PROBE_COMPARATOR = Comparator.comparingLong(p -> p.lastProbeMillis);
    private static final String STATUS_TAG = "status";
    private static final Map<BlockNodeStatus, String> STATUS_TAG_VALUES = Arrays.stream(BlockNodeStatus.values())
            .filter(not(isEqual(BlockNodeStatus.IGNORED)))
            .collect(Collectors.toUnmodifiableMap(
                    Function.identity(), s -> s.name().toLowerCase(Locale.ROOT)));

    private final BlockStreamReader blockStreamReader;
    private final CutoverService cutoverService;
    private final LatencyServiceProperties latencyServiceProperties;
    private final MeterRegistry meterRegistry;
    private final MultiGauge statusMetric;

    private volatile Set<BlockNode> nodes = Set.of();

    // Only accessed by the scheduled runs, which never overlap
    private final Map<BlockNode, Probe> probes = new HashMap<>();

    public LatencyService(
            final BlockStreamReader blockStreamReader,
            final CutoverService cutoverService,
            final LatencyServiceProperties latencyServiceProperties,
            final MeterRegistry meterRegistry) {
        this.blockStreamReader = blockStreamReader;
        this.cutoverService = cutoverService;
        this.latencyServiceProperties = latencyServiceProperties;
        this.meterRegistry = meterRegistry;
        statusMetric = MultiGauge.builder(STATUS_METRIC_NAME)
                .description("The status of the last latency probe of a block node in the status tag, always 1")
                .register(meterRegistry);
    }

    @Override
    public void close() {
        nodes = Set.of();
    }

    @Scheduled(fixedDelayString = "#{@latencyServiceProperties.getFrequency().toMillis()}")
    public void schedule() {
        final var current = nodes;
        if (probes.keySet().retainAll(current)) {
            registerStatusMetric();
        }

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

    private void registerStatusMetric() {
        final var rows = new ArrayList<MultiGauge.Row<?>>(probes.size());
        for (final var probe : probes.values()) {
            final var status = probe.status;
            if (status == null) {
                continue;
            }

            rows.add(MultiGauge.Row.of(
                    Tags.of(
                            BLOCK_NODE_TAG,
                            probe.node.getStatusEndpoint(),
                            STATUS_TAG,
                            Objects.requireNonNull(STATUS_TAG_VALUES.get(status))),
                    1));
        }

        statusMetric.register(rows);
    }

    private final class Probe {

        private long backoffMillis;
        private long lastMeasuredBlockNumber = -1;
        private long lastProbeMillis;
        private long nextProbeMillis;
        private final BlockNode node;
        private int notAvailable;
        private @Nullable BlockNodeStatus status;

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
                onFailure(BlockNodeStatus.of(ex), ex);
            }

            return true;
        }

        private void markStale(final BlockNodeStatus failure, final Throwable cause) {
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

        private void onFailure(final BlockNodeStatus failure, final Exception cause) {
            if (failure == BlockNodeStatus.IGNORED) {
                return;
            }

            final long frequency = latencyServiceProperties.getFrequency().toMillis();
            final long maxBackoff = latencyServiceProperties.getMaxBackoff().toMillis();
            backoffMillis = backoffMillis == 0 ? frequency : Math.min(backoffMillis * 2, maxBackoff);
            nextProbeMillis = lastProbeMillis + backoffMillis;
            log.debug("Latency probe of {} failed with {}", node, failure, cause);
            markStale(failure, cause);
            updateStatus(failure);
        }

        private void onNotAvailable() {
            // The node answered, so a later failure starts over from the base backoff
            backoffMillis = 0;
            if (++notAvailable >= NOT_AVAILABLE_STALE_THRESHOLD
                    && node.getLatency().markStale()) {
                log.warn("Marked latency of {} stale after {} latency probes without the block", node, notAvailable);
            }

            updateStatus(BlockNodeStatus.NOT_AVAILABLE);
        }

        private void onSuccess(final long blockNumber) {
            if (backoffMillis > 0) {
                log.info("Measured latency of {} again after failed latency probes", node);
            }

            backoffMillis = 0;
            lastMeasuredBlockNumber = blockNumber;
            nextProbeMillis = 0;
            notAvailable = 0;
            updateStatus(BlockNodeStatus.HEALTHY);
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

        private void updateStatus(final BlockNodeStatus status) {
            meterRegistry
                    .counter(
                            PROBE_METRIC_NAME,
                            BLOCK_NODE_TAG,
                            node.getStatusEndpoint(),
                            STATUS_TAG,
                            Objects.requireNonNull(STATUS_TAG_VALUES.get(status)))
                    .increment();
            if (this.status == status) {
                return;
            }

            this.status = status;
            registerStatusMetric();
        }
    }
}
