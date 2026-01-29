// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;

final class PriorityScheduler extends AbstractScheduler {

    private final List<BlockNode> nodes;

    PriorityScheduler(
            final Collection<BlockNodeProperties> blockNodeProperties,
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final MeterRegistry meterRegistry,
            final StreamProperties streamProperties) {
        nodes = blockNodeProperties.stream()
                .map(properties -> new BlockNode(
                        channelBuilderProvider, this::drainGrpcBuffer, meterRegistry, properties, streamProperties))
                .sorted()
                .toList();
    }

    @Override
    protected Iterator<BlockNode> getOrderedNodes() {
        return nodes.iterator();
    }
}
