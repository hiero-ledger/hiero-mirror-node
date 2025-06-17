// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.springframework.beans.factory.DisposableBean;

@Named
public final class BlockNodeSubscriber extends AbstractBlockSource implements DisposableBean {

    private static final long UNKNOWN_NODE_ID = -1;

    private final Map<Integer, List<BlockNode>> nodes = new TreeMap<>();

    BlockNodeSubscriber(
            BlockStreamReader blockStreamReader,
            BlockStreamVerifier blockStreamVerifier,
            CommonDownloaderProperties commonDownloaderProperties,
            BlockProperties properties) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, properties);

        for (var nodeProperties : properties.getNodes()) {
            var node = new BlockNode(nodeProperties, properties.getStream());
            nodes.compute(nodeProperties.getPriority(), (priority, priorityGroup) -> {
                if (priorityGroup == null) {
                    priorityGroup = new ArrayList<>();
                }

                priorityGroup.add(node);
                return priorityGroup;
            });
        }
    }

    @Override
    public void destroy() {
        nodes.values().stream().flatMap(List::stream).forEach(BlockNode::destroy);
    }

    @Override
    public void get() {
        long blockNumber = getNextBlockNumber();
        var node = getNode(blockNumber);
        if (node == null) {
            throw new BlockStreamException("No block node can provide block " + blockNumber);
        }

        log.info("Start streaming block {} from {}", blockNumber, node);
        node.streamBlocks(
                blockNumber,
                commonDownloaderProperties.getTimeout(),
                streamedBlock -> onBlockStream(toBlockStream(streamedBlock)));
    }

    private BlockNode getNode(long blockNumber) {
        var inactiveNodes = new ArrayList<BlockNode>();
        for (int priority : nodes.keySet()) {
            for (var node : nodes.get(priority)) {
                if (!node.tryReadmit(false).isActive()) {
                    inactiveNodes.add(node);
                    continue;
                }

                if (node.hasBlock(blockNumber)) {
                    return node;
                }
            }
        }

        // find the first inactive node with the block and force activating it
        for (var node : inactiveNodes) {
            if (node.hasBlock(blockNumber)) {
                node.tryReadmit(true);
                return node;
            }
        }

        return null;
    }

    private BlockStream toBlockStream(BlockNode.StreamedBlock streamedBlock) {
        var blockItems = streamedBlock.blockItems();
        var blockHeader = blockItems.getFirst().getBlockHeader();
        var filename = BlockFile.getFilename(blockHeader.getNumber(), false);
        return new BlockStream(blockItems, null, filename, streamedBlock.loadStart(), UNKNOWN_NODE_ID);
    }
}
