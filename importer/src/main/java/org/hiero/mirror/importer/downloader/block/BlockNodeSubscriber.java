// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.google.common.collect.TreeMultimap;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;

@Named
final class BlockNodeSubscriber extends AbstractBlockSource implements AutoCloseable {

    private final TreeMultimap<Integer, BlockNode> nodes;
    private final AtomicReference<BlockNode> current = new AtomicReference<>();
    private final AtomicLong lastPostProcessingLatency = new AtomicLong();
    private Instant lastScheduleTime = Instant.now();

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
                .collect(
                        TreeMultimap::create,
                        (map, blockNode) -> map.put(blockNode.getProperties().getPriority(), blockNode),
                        TreeMultimap::putAll);
    }

    @Override
    public void close() {
        nodes.forEach((i, blockNode) -> blockNode.close());
    }

    @Override
    public void get() {
        long blockNumber = getNextBlockNumber();
        var endBlockNumber = commonDownloaderProperties.getImporterProperties().getEndBlockNumber();

        if (endBlockNumber != null && blockNumber > endBlockNumber) {
            return;
        }

        var node = getNode(blockNumber);
        current.set(node);
        lastScheduleTime = Instant.now();
        log.info("Start streaming block {} from {}", blockNumber, node);

        try {
            node.streamBlocks(blockNumber, commonDownloaderProperties, this::handleBlockStream);
        } finally {
            lastPostProcessingLatency.set(0);
        }
    }

    private boolean handleBlockStream(BlockStream blockStream) {
        long start = System.currentTimeMillis();
        var blockFile = onBlockStream(blockStream);

        var scheduling = properties.getScheduling();
        if (!scheduling.isLatencySchedulingEnabled()) {
            return false;
        }

        long previousPostProcessingLatency = lastPostProcessingLatency.getAndSet(System.currentTimeMillis() - start);
        int priority = current.get().getProperties().getPriority();
        if (previousPostProcessingLatency
                <= scheduling.getMaxPostProcessingLatency().toMillis()) {
            // when post-processing takes too long, it can cause a significant delay between data reception and the
            // assembly of the block for post-processing. Therefore, latency should only be measured and recorded under
            // low post-processing latency conditions
            long latency = start - blockFile.getConsensusEnd() / 1_000_000;
            current.get().recordLatency(latency);
            // remove and add the node back to sort it in the priority group
            nodes.remove(priority, current.get());
            nodes.put(priority, current.get());
        }

        if (lastScheduleTime.plus(scheduling.getMinRescheduleInterval()).isAfter(Instant.now())
                && nodes.get(priority).size() > 1) {
            for (var other : nodes.get(priority)) {
                if (!other.tryReadmit(false).isActive()) {
                    continue;
                }

                // should try to reschedule if the first active node is not the current node
                boolean shouldReschedule = !other.equals(current.get());
                if (shouldReschedule) {
                    log.info("Try to reschedule to select a block node with lower latency");
                }

                return shouldReschedule;
            }
        }

        return false;
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

        throw new BlockStreamException("No block node can provide block " + blockNumber);
    }
}
