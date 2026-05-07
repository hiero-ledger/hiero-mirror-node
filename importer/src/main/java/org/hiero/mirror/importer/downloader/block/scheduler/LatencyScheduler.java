// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.hiero.mirror.importer.downloader.block.BlockNode.LATENCY_COMPARATOR;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;

final class LatencyScheduler extends AbstractLatencyAwareScheduler {

    private final AtomicReference<List<BlockNode>> nodes = new AtomicReference<>(Collections.emptyList());

    LatencyScheduler(
            final BlockNodeDiscoveryService blockNodeDiscoveryService,
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final LatencyService latencyService,
            final MeterRegistry meterRegistry,
            final SchedulerProperties schedulerProperties,
            final StreamProperties streamProperties) {
        super(
                blockNodeDiscoveryService,
                channelBuilderProvider,
                latencyService,
                meterRegistry,
                schedulerProperties,
                streamProperties);
    }

    @Override
    protected Iterator<BlockNode> getNodeGroupIterator() {
        return Objects.requireNonNull(nodes.get()).iterator();
    }

    @Override
    protected Iterator<BlockNode> getOrderedNodes() {
        final var current = Objects.requireNonNull(nodes.get());
        current.sort(LATENCY_COMPARATOR);
        return current.iterator();
    }

    @Override
    protected void setNodes(final List<BlockNode> blockNodes) {
        blockNodes.sort(LATENCY_COMPARATOR);
        nodes.set(blockNodes);
    }
}
