// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.jspecify.annotations.Nullable;

abstract class AbstractLatencyAwareScheduler extends AbstractScheduler {

    static final String LATENCY_METRIC_NAME = "hiero.mirror.importer.block.latency";

    private static final Comparator<SortKey> SORT_KEY_COMPARATOR = Comparator.comparingDouble(SortKey::average)
            .thenComparing(k -> k.node().getSubscribeStreamName());

    protected final LatencyService latencyService;
    protected final SchedulerProperties schedulerProperties;

    protected final List<BlockNode> candidates = new CopyOnWriteArrayList<>();
    protected final AtomicReference<@Nullable BlockNode> current = new AtomicReference<>();
    protected final AtomicLong lastScheduledTime = new AtomicLong(0);

    private volatile long lastPostProcessingLatency;
    private final MultiGauge latencyMetric;

    AbstractLatencyAwareScheduler(
            final BlockNodeDiscoveryService blockNodeDiscoveryService,
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final LatencyService latencyService,
            final MeterRegistry meterRegistry,
            final SchedulerProperties schedulerProperties,
            final StreamProperties streamProperties) {
        super(blockNodeDiscoveryService, channelBuilderProvider, meterRegistry, streamProperties);
        this.latencyService = latencyService;
        this.schedulerProperties = schedulerProperties;
        this.latencyMetric = MultiGauge.builder(LATENCY_METRIC_NAME)
                .description("The exponential moving average of a block node's streaming latency")
                .baseUnit("milliseconds")
                .register(meterRegistry);
    }

    @Override
    public ScheduledBlockNode getNode(final long blockNumber) {
        try {
            final var scheduled = super.getNode(blockNumber);
            current.set(scheduled.blockNode());
            candidates.clear();
            candidates.addAll(getCandidates());
            latencyService.setNodes(candidates);
            lastScheduledTime.set(System.currentTimeMillis());
            return scheduled;
        } catch (BlockStreamException ex) {
            current.set(null);
            throw ex;
        }
    }

    @Override
    public boolean shouldReschedule(final BlockFile blockFile, final BlockStream blockStream) {
        final long previousPostProcessingLatency = lastPostProcessingLatency;
        lastPostProcessingLatency = System.currentTimeMillis() - blockStream.blockCompleteTime();

        // when post-processing takes too long, it can significantly delay block stream response processing and skew the
        // latency. Therefore, latency should only be measured and recorded under low post-processing latency conditions
        if (previousPostProcessingLatency
                > schedulerProperties.getMaxPostProcessingLatency().toMillis()) {
            return false;
        }

        final var node = Objects.requireNonNull(current.get());
        final var latency = node.getLatency();
        latency.record(Utils.getLatency(blockFile, blockStream));

        if (System.currentTimeMillis() - lastScheduledTime.get()
                < schedulerProperties.getMinRescheduleInterval().toMillis()) {
            return false;
        }

        final double average = latency.getAverage();
        final long maxLatencyAgeMillis = latencyService.getMaxLatencyAgeMillis();
        final long threshold =
                schedulerProperties.getRescheduleLatencyThreshold().toMillis();
        for (var candidate : candidates) {
            final var candidateLatency = candidate.getLatency();
            if (!candidateLatency.isFresh(maxLatencyAgeMillis)) {
                // don't reschedule to a candidate based on a measurement that may no longer reflect its condition
                continue;
            }

            final var candidateAverage = candidateLatency.getAverage();
            if (average - candidateAverage >= threshold) {
                log.info(
                        "Will stop streaming from {} and switch to a lower-latency node",
                        node.getSubscribeStreamName());
                return true;
            }
        }

        return false;
    }

    protected abstract Iterator<BlockNode> getNodeGroupIterator();

    @Override
    protected void onNodesChanged(final List<BlockNode> blockNodes) {
        final var rows = new ArrayList<MultiGauge.Row<?>>(blockNodes.size());
        for (final var node : blockNodes) {
            rows.add(MultiGauge.Row.of(
                    Tags.of("block_node", node.getEndpoint()),
                    node,
                    n -> n.getLatency().getMetricValue()));
        }

        latencyMetric.register(rows, true);
    }

    /**
     * Sorts the block nodes by latency, treating a latency sample older than the freshness limit as unmeasured.
     */
    protected void sortByLatency(final List<BlockNode> blockNodes) {
        // Snapshot the averages so a concurrent measurement can't break the sort
        final long maxLatencyAgeMillis = latencyService.getMaxLatencyAgeMillis();
        final var sorted = new ArrayList<SortKey>(blockNodes.size());
        for (final var node : blockNodes) {
            sorted.add(new SortKey(node, node.getLatency().getAverage(maxLatencyAgeMillis)));
        }

        sorted.sort(SORT_KEY_COMPARATOR);
        for (int i = 0; i < sorted.size(); i++) {
            blockNodes.set(i, sorted.get(i).node());
        }
    }

    private Collection<BlockNode> getCandidates() {
        final var iter = getNodeGroupIterator();
        final var active = current.get();
        final var candidates = new ArrayList<BlockNode>();
        while (iter.hasNext()) {
            var node = iter.next();
            if (node == active) {
                continue;
            }

            candidates.add(node);
        }

        return Collections.unmodifiableCollection(candidates);
    }

    private record SortKey(BlockNode node, double average) {}
}
