// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import io.grpc.stub.BlockingClientCall;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class BlockNodeSubscriber extends AbstractBlockSource implements AutoCloseable {

    private final ManagedChannelBuilderProvider channelBuilderProvider;
    private final BlockNodeDiscoveryService blockNodeDiscoveryService;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor;

    private final AtomicReference<List<BlockNode>> nodes = new AtomicReference<>(Collections.emptyList());

    BlockNodeSubscriber(
            final BlockStreamReader blockStreamReader,
            final BlockStreamVerifier blockStreamVerifier,
            final CommonDownloaderProperties commonDownloaderProperties,
            final CutoverService cutoverService,
            final ManagedChannelBuilderProvider channelBuilderProvider,
            final BlockNodeDiscoveryService blockNodeDiscoveryService,
            final BlockProperties properties,
            final MeterRegistry meterRegistry) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, cutoverService, properties);
        this.channelBuilderProvider = channelBuilderProvider;
        this.blockNodeDiscoveryService = blockNodeDiscoveryService;
        this.meterRegistry = meterRegistry;
        this.executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void close() {
        getBlockNodes().forEach(BlockNode::close);
        executor.shutdown();
    }

    @Override
    protected void doGet(final long blockNumber) {
        final var nextBlockNumber = new AtomicLong(blockNumber);
        final var node = getNode(nextBlockNumber);
        if (blockNumber == EARLIEST_AVAILABLE_BLOCK_NUMBER && !shouldGetBlock(nextBlockNumber.get())) {
            return;
        }

        log.info("Start streaming block {} from {}", nextBlockNumber.get(), node);
        node.streamBlocks(
                nextBlockNumber.get(),
                commonDownloaderProperties,
                (stream) -> onBlockStream(stream, node.getProperties().getStatusEndpoint()));
    }

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

    /**
     * Returns the current block nodes, recreating them when the merged node properties have changed
     * (e.g. due to config or discovery updates).
     */
    private synchronized List<BlockNode> getBlockNodes() {
        final var latestPropertiesList = blockNodeDiscoveryService.getBlockNodesPropertiesList(properties).stream()
                .sorted()
                .toList();

        final List<BlockNode> currentNodes = Objects.requireNonNullElse(nodes.get(), Collections.emptyList());
        if (!nodesChanged(currentNodes, latestPropertiesList)) {
            return currentNodes;
        }

        currentNodes.forEach(BlockNode::close);
        final var newNodes = latestPropertiesList.stream()
                .map(props -> new BlockNode(
                        channelBuilderProvider, this::drainGrpcBuffer, props, properties.getStream(), meterRegistry))
                .sorted()
                .toList();
        nodes.set(newNodes);
        return newNodes;
    }

    private static boolean nodesChanged(List<BlockNode> current, List<BlockNodeProperties> newProperties) {
        if (current.size() != newProperties.size()) {
            return true; // Node was added or removed
        }
        for (int i = 0; i < current.size(); i++) {
            if (!Objects.equals(current.get(i).getProperties(), newProperties.get(i))) {
                return true; // A host, port, or TLS setting changed
            }
        }

        return false;
    }

    private BlockNode getNode(final AtomicLong nextBlockNumber) {
        final var nodeList = getBlockNodes();
        final var inactiveNodes = new ArrayList<BlockNode>();
        for (final var node : nodeList) {
            if (!node.tryReadmit(false).isActive()) {
                inactiveNodes.add(node);
                continue;
            }

            if (hasBlock(nextBlockNumber, node)) {
                return node;
            }
        }

        // find the first inactive node with the block and force activating it
        for (final var node : inactiveNodes) {
            if (hasBlock(nextBlockNumber, node)) {
                node.tryReadmit(true);
                return node;
            }
        }

        throw new BlockStreamException("No block node can provide block " + nextBlockNumber.get());
    }

    private static boolean hasBlock(final AtomicLong nextBlockNumber, final BlockNode node) {
        final var blockRange = node.getBlockRange();
        if (blockRange.isEmpty()) {
            return false;
        }

        if (nextBlockNumber.get() == EARLIEST_AVAILABLE_BLOCK_NUMBER) {
            nextBlockNumber.set(blockRange.lowerEndpoint());
            return true;
        } else {
            return blockRange.contains(nextBlockNumber.get());
        }
    }
}
