// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Value;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.jspecify.annotations.Nullable;

final class PriorityAndLatencyScheduler extends AbstractLatencyAwareScheduler {

    private final AtomicReference<TreeMap<Integer, PriorityGroup>> priorityGroups =
            new AtomicReference<>(new TreeMap<>());

    PriorityAndLatencyScheduler(
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
        final var blockNode = current.get();
        if (blockNode == null) {
            return Collections.emptyIterator();
        }

        final int priority = blockNode.getProperties().getPriority();
        final var group = Objects.requireNonNull(priorityGroups.get()).get(priority);
        return group != null ? group.getIterator() : Collections.emptyIterator();
    }

    @Override
    protected Iterator<BlockNode> getOrderedNodes() {
        return new Iterator<>() {

            private final Iterator<PriorityGroup> groupIter =
                    Objects.requireNonNull(priorityGroups.get()).values().iterator();

            @Nullable
            private Iterator<BlockNode> nodeGroupIterator;

            @Override
            public boolean hasNext() {
                if (nodeGroupIterator == null || !nodeGroupIterator.hasNext()) {
                    if (groupIter.hasNext()) {
                        nodeGroupIterator = groupIter.next().sort().getIterator();
                    }
                }

                return nodeGroupIterator != null && nodeGroupIterator.hasNext();
            }

            @Override
            public BlockNode next() {
                return Objects.requireNonNull(nodeGroupIterator).next();
            }
        };
    }

    @Override
    protected void setNodes(final List<BlockNode> blockNodes) {
        final var nodeGroups = new TreeMap<Integer, PriorityGroup>();
        for (final var blockNode : blockNodes) {
            final int priority = blockNode.getProperties().getPriority();
            nodeGroups.computeIfAbsent(priority, PriorityGroup::new).getNodes().add(blockNode);
        }

        priorityGroups.set(nodeGroups);
    }

    @Value
    private static class PriorityGroup {

        private final List<BlockNode> nodes;
        private final int priority;

        PriorityGroup(final int priority) {
            this.priority = priority;
            this.nodes = new ArrayList<>();
        }

        PriorityGroup sort() {
            nodes.sort(BlockNode.LATENCY_COMPARATOR);
            return this;
        }

        Iterator<BlockNode> getIterator() {
            return nodes.iterator();
        }
    }
}
