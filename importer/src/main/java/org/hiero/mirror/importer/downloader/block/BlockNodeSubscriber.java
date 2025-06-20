// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.springframework.beans.factory.DisposableBean;

@Named
final class BlockNodeSubscriber extends AbstractBlockSource implements DisposableBean {

    private static final long UNKNOWN_NODE_ID = -1;

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
    public void destroy() {
        nodes.forEach(BlockNode::destroy);
    }

    @Override
    public void get() {
        long blockNumber = getNextBlockNumber();
        var node = getNode(blockNumber);

        log.info("Start streaming block {} from {}", blockNumber, node);
        node.streamBlocks(
                blockNumber,
                commonDownloaderProperties.getTimeout(),
                streamedBlock -> onBlockStream(toBlockStream(streamedBlock)));
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

    private BlockStream toBlockStream(BlockNode.StreamedBlock streamedBlock) {
        var blockItems = streamedBlock.blockItems();
        var blockHeader = blockItems.getFirst().getBlockHeader();
        var filename = BlockFile.getFilename(blockHeader.getNumber(), false);
        return new BlockStream(blockItems, null, filename, streamedBlock.loadStart(), UNKNOWN_NODE_ID);
    }
}
