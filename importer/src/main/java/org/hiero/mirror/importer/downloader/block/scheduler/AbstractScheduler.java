// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import java.util.ArrayList;
import java.util.Iterator;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.exception.BlockStreamException;

abstract class AbstractScheduler implements Scheduler {

    @Override
    public final void close() {
        getOrderedNodes().forEachRemaining(BlockNode::close);
    }

    @Override
    public BlockNode getNode(long blockNumber) {
        var inactiveNodes = new ArrayList<BlockNode>();
        var iter = getOrderedNodes();
        while (iter.hasNext()) {
            var node = iter.next();
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

    protected abstract Iterator<BlockNode> getOrderedNodes();
}
