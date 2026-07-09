// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.grpc.stub.BlockingClientCall;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.downloader.block.BlockNodeDiscoveryService;
import org.hiero.mirror.importer.downloader.block.BlockNodeProperties;
import org.hiero.mirror.importer.downloader.block.ManagedChannelBuilderProvider;
import org.hiero.mirror.importer.downloader.block.StreamProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractScheduler implements Scheduler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final BlockNodeDiscoveryService blockNodeDiscoveryService;
    private final ManagedChannelBuilderProvider channelBuilderProvider;
    private final AtomicReference<List<BlockNodeProperties>> discovered =
            new AtomicReference<>(Collections.emptyList());
    private final ExecutorService executor;
    private final MeterRegistry meterRegistry;
    private final StreamProperties streamProperties;

    protected AbstractScheduler(
            final BlockNodeDiscoveryService blockNodeDiscoveryService,
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final MeterRegistry meterRegistry,
            final StreamProperties streamProperties) {
        this.blockNodeDiscoveryService = blockNodeDiscoveryService;
        this.channelBuilderProvider = channelBuilderProvider;
        this.meterRegistry = meterRegistry;
        this.streamProperties = streamProperties;
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public final void close() {
        getOrderedNodes().forEachRemaining(BlockNode::close);
        executor.close();
    }

    @Override
    public ScheduledBlockNode getNode(final long blockNumber) {
        refreshNodes();

        final var inactiveNodes = new ArrayList<BlockNode>();
        final var iter = getOrderedNodes();
        while (iter.hasNext()) {
            var node = iter.next();
            if (!node.tryReadmit(false).isActive()) {
                inactiveNodes.add(node);
                continue;
            }

            final var choice = hasBlock(blockNumber, node);
            if (choice != null) {
                return choice;
            }
        }

        // find the first inactive node with the block and force activating it
        for (var node : inactiveNodes) {
            final var choice = hasBlock(blockNumber, node);
            if (choice != null) {
                node.tryReadmit(true);
                return choice;
            }
        }

        throw new BlockStreamException("No block node can provide block " + blockNumber);
    }

    protected abstract Iterator<BlockNode> getOrderedNodes();

    protected abstract void setNodes(final List<BlockNode> blockNodes);

    private void drainGrpcBuffer(final BlockingClientCall<?, ?> grpcCall) {
        // Run a task to drain grpc buffer to avoid memory leak. Remove the logic when grpc-java releases the fix for
        // https://github.com/grpc/grpc-java/issues/12355
        executor.submit(() -> {
            try {
                while (grpcCall.read() != null) {
                    log.debug("Drained grpc buffer");
                }
            } catch (Exception ex) {
                log.debug("Exception when trying to drain grpc buffer", ex);
            }
        });
    }

    private void refreshNodes() {
        final var current = Objects.requireNonNull(discovered.get());
        final var next = blockNodeDiscoveryService.getBlockNodes();
        if (!nodesChanged(current, next)) {
            return;
        }

        discovered.set(next);
        final var nodes = new ArrayList<BlockNode>(next.size());
        for (final var blockNodeProperties : next) {
            nodes.add(new BlockNode(
                    channelBuilderProvider,
                    this::drainGrpcBuffer,
                    meterRegistry,
                    blockNodeProperties,
                    streamProperties));
        }

        setNodes(nodes);
    }

    private static @Nullable ScheduledBlockNode hasBlock(final long nextBlockNumber, final BlockNode node) {
        final var blockRange = node.getBlockRange();
        if (blockRange.isEmpty()) {
            return null;
        }

        if (nextBlockNumber == EARLIEST_AVAILABLE_BLOCK_NUMBER) {
            return new ScheduledBlockNode(node, blockRange.lowerEndpoint());
        } else if (blockRange.contains(nextBlockNumber)) {
            return new ScheduledBlockNode(node, nextBlockNumber);
        }

        return null;
    }

    private static boolean nodesChanged(final List<BlockNodeProperties> current, final List<BlockNodeProperties> next) {
        if (current.size() != next.size()) {
            return true; // Node was added or removed
        }

        for (int i = 0; i < current.size(); i++) {
            if (!Objects.equals(current.get(i), next.get(i))) {
                return true; // A host, port, or TLS setting changed
            }
        }

        return false;
    }
}
