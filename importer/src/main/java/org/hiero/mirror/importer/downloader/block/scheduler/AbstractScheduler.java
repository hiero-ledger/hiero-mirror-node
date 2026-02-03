// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import io.grpc.stub.BlockingClientCall;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractScheduler implements Scheduler {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final ExecutorService executor;

    protected AbstractScheduler() {
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public final void close() {
        getOrderedNodes().forEachRemaining(BlockNode::close);
    }

    @Override
    public BlockNode getNode(final AtomicLong blockNumber) {
        var inactiveNodes = new ArrayList<BlockNode>();
        var iter = getOrderedNodes();
        while (iter.hasNext()) {
            var node = iter.next();
            if (!node.tryReadmit(false).isActive()) {
                inactiveNodes.add(node);
                continue;
            }

            if (hasBlock(blockNumber, node)) {
                return node;
            }
        }

        // find the first inactive node with the block and force activating it
        for (var node : inactiveNodes) {
            if (hasBlock(blockNumber, node)) {
                node.tryReadmit(true);
                return node;
            }
        }

        throw new BlockStreamException("No block node can provide block " + blockNumber);
    }

    protected abstract Iterator<BlockNode> getOrderedNodes();

    protected void drainGrpcBuffer(final BlockingClientCall<?, ?> grpcCall) {
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
