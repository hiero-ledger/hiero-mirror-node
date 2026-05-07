// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

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

final class PriorityScheduler extends AbstractScheduler {

    private final AtomicReference<List<BlockNode>> nodes = new AtomicReference<>(Collections.emptyList());

    PriorityScheduler(
            final BlockNodeDiscoveryService blockNodeDiscoveryService,
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final MeterRegistry meterRegistry,
            final StreamProperties streamProperties) {
        super(blockNodeDiscoveryService, channelBuilderProvider, meterRegistry, streamProperties);
    }

    @Override
    protected void setNodes(final List<BlockNode> newNodes) {
        Collections.sort(newNodes);
        this.nodes.set(List.copyOf(newNodes));
    }

    @Override
    protected Iterator<BlockNode> getOrderedNodes() {
        return Objects.requireNonNull(nodes.get()).iterator();
    }
}
