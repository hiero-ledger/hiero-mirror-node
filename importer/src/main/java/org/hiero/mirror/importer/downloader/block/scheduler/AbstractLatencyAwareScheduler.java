// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import com.google.common.collect.Lists;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
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

    protected final LatencyService latencyService;
    protected final SchedulerProperties schedulerProperties;

    protected final List<BlockNode> candidates = new CopyOnWriteArrayList<>();
    protected final AtomicReference<@Nullable BlockNode> current = new AtomicReference<>();
    protected final AtomicLong lastScheduledTime = new AtomicLong(0);

    private volatile long lastPostProcessingLatency;

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

        final long latency = Utils.getLatency(blockFile, blockStream);
        final var node = Objects.requireNonNull(current.get());
        node.recordLatency(latency);

        if (System.currentTimeMillis() - lastScheduledTime.get()
                < schedulerProperties.getMinRescheduleInterval().toMillis()) {
            return false;
        }

        final double updatedLatency = node.getLatency();
        final long threshold =
                schedulerProperties.getRescheduleLatencyThreshold().toMillis();
        for (var candidate : candidates) {
            if (updatedLatency >= candidate.getLatency() + threshold) {
                return true;
            }
        }

        return false;
    }

    protected abstract Iterator<BlockNode> getNodeGroupIterator();

    private Collection<BlockNode> getCandidates() {
        final var iter = getNodeGroupIterator();
        final var active = current.get();
        while (iter.hasNext()) {
            var node = iter.next();
            if (node == active) {
                break;
            }
        }

        return Lists.newArrayList(iter);
    }
}
