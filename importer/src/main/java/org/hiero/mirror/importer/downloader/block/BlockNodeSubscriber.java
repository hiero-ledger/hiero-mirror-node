// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;

@Named
final class BlockNodeSubscriber extends AbstractBlockSource implements AutoCloseable {

    private final List<BlockNode> nodes;

    BlockNodeSubscriber(
            BlockStreamReader blockStreamReader,
            BlockStreamVerifier blockStreamVerifier,
            CommonDownloaderProperties commonDownloaderProperties,
            ManagedChannelBuilderProvider channelBuilderProvider,
            BlockProperties properties) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, properties);
        nodes = properties.getNodes().stream()
                .map(blockNodeProperties ->
                        new BlockNode(channelBuilderProvider, blockNodeProperties, properties.getStream()))
                .sorted()
                .toList();
    }

    @Override
    public void close() {
        nodes.forEach(BlockNode::close);
    }

    @Override
    public void get() {
        long blockNumber = getNextBlockNumber();
        var endBlockNumber = commonDownloaderProperties.getImporterProperties().getEndBlockNumber();

        if (endBlockNumber != null && blockNumber > endBlockNumber) {
            return;
        }

        var node = getNode(blockNumber);
        log.info("Start streaming block {} from {}", blockNumber, node);
        log.info("STREAMING BLOCKS....");
        node.streamBlocks(blockNumber, commonDownloaderProperties, this::onBlockStream);
    }

    private BlockNode getNode(long blockNumber) {
        var inactiveNodes = new ArrayList<BlockNode>();
        for (var node : nodes) {
            if (!node.tryReadmit(false).isActive()) {
                inactiveNodes.add(node);
                continue;
            }

            if (node.hasBlock(blockNumber)) {
                return node;
            }
        }

        // find the first inactive node with the block and force activating it
        for (var node : inactiveNodes) {
            if (node.hasBlock(blockNumber)) {
                node.tryReadmit(true);
                return node;
            }
        }

        throw new BlockStreamException("No block node can provide block " + blockNumber);
    }
}
