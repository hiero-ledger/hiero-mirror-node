// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

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
            Collection<BlockNodeProperties> blockNodeProperties,
            ManagedChannelBuilderProvider channelBuilderProvider,
            StreamProperties streamProperties) {
        nodes = blockNodeProperties.stream()
                .map(properties -> new BlockNode(channelBuilderProvider, properties, streamProperties))
                .sorted()
                .toList();
    }

    @Override
    protected Iterator<BlockNode> getOrderedNodes() {
        return nodes.iterator();
    }
}
