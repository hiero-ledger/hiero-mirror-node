// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import lombok.Value;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.SchedulerProperties;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.jspecify.annotations.Nullable;

final class PriorityAndLatencyScheduler extends AbstractLatencyAwareScheduler {

    private final TreeMap<Integer, PriorityGroup> priorityGroups;

    PriorityAndLatencyScheduler(
            final Collection<BlockNodeProperties> blockNodeProperties,
            final LatencyService latencyService,
            final ManagedChannelBuilderProvider managedChannelBuilderProvider,
            final MeterRegistry meterRegistry,
            final SchedulerProperties schedulerProperties,
            final StreamProperties streamProperties) {
        super(latencyService, schedulerProperties);

        priorityGroups = new TreeMap<>();
        for (var blockNodeProperty : blockNodeProperties) {
            var node = new BlockNode(
                    managedChannelBuilderProvider,
                    this::drainGrpcBuffer,
                    meterRegistry,
                    blockNodeProperty,
                    streamProperties);
            priorityGroups
                    .computeIfAbsent(node.getPriority(), PriorityGroup::new)
                    .getNodes()
                    .add(node);
        }
    }

    @Override
    protected Iterator<BlockNode> getOrderedNodes() {
        return new Iterator<>() {

            private final Iterator<PriorityGroup> groupIter =
                    priorityGroups.values().iterator();

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
    protected Iterator<BlockNode> getNodeGroupIterator() {
        return Objects.requireNonNull(
                        priorityGroups.get(Objects.requireNonNull(current.get()).getPriority()))
                .getIterator();
    }

    @Value
    private static class PriorityGroup {

        private final List<BlockNode> nodes;
        private final int priority;

        PriorityGroup(int priority) {
            this.priority = priority;
            this.nodes = new ArrayList<>();
        }

        PriorityGroup sort() {
            nodes.sort(BlockNode::compareTo);
            return this;
        }

        Iterator<BlockNode> getIterator() {
            return nodes.iterator();
        }
    }
}
