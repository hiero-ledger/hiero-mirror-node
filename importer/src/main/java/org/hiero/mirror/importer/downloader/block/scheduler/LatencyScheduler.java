// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import static org.hiero.mirror.importer.downloader.block.BlockNode.LATENCY_COMPARATOR;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.SchedulerProperties;
import org.hiero.mirror.importer.downloader.block.StreamProperties;

final class LatencyScheduler extends AbstractLatencyAwareScheduler {

    private final List<BlockNode> nodes;

    LatencyScheduler(
            final Collection<BlockNodeProperties> blockNodeProperties,
            final LatencyService latencyService,
            final ManagedChannelBuilderProvider managedChannelBuilderProvider,
            final MeterRegistry meterRegistry,
            final SchedulerProperties schedulerProperties,
            final StreamProperties streamProperties) {
        super(latencyService, schedulerProperties);
        nodes = blockNodeProperties.stream()
                .map(properties -> new BlockNode(
                        managedChannelBuilderProvider,
                        this::drainGrpcBuffer,
                        meterRegistry,
                        properties,
                        streamProperties))
                .sorted(LATENCY_COMPARATOR)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    protected Iterator<BlockNode> getNodeGroupIterator() {
        return nodes.iterator();
    }

    @Override
    protected Iterator<BlockNode> getOrderedNodes() {
        nodes.sort(LATENCY_COMPARATOR);
        return nodes.iterator();
    }
}
