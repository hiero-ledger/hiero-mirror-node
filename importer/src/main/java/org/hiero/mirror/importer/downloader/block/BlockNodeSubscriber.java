// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.Getter;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.exception.BlockStreamException;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import reactor.core.Exceptions;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Named
public final class BlockNodeSubscriber extends AbstractBlockSource {

    private final Map<Integer, List<BlockNode>> nodes = new TreeMap<>();

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Scheduler scheduler = createScheduler();

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
    public void get() {
        long blockNumber = getNextBlockNumber();
        var node = getNode(blockNumber);
        if (node == null) {
            throw new BlockStreamException("No block node can provide block " + blockNumber);
        }

        log.info("Start streaming block {} from {}", blockNumber, node);
        node.stream(blockNumber)
                .timeout(commonDownloaderProperties.getTimeout())
                .onBackpressureBuffer(properties.getStream().getMaxBufferSize())
                // publish on a different thread since the downstream operations may be slower
                .publishOn(getScheduler())
                .map(this::toBlockStream)
                .doOnNext(this::onBlockStream)
                // ignore overflow error which happens when block processing is slower than streaming
                .onErrorComplete(Exceptions::isOverflow)
                .doOnError(e -> {
                    log.error("Error streaming block from {}", node, e);
                    node.onError();
                })
                .blockLast();
    }

    private BlockNode getNode(long blockNumber) {
        List<BlockNode> inactiveNodes = new ArrayList<>();
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

    private Scheduler createScheduler() {
        return Schedulers.newSingle(this.getClass().getSimpleName());
    }

    private BlockStream toBlockStream(BlockNode.StreamedBlock streamedBlock) {
        var blockItems = streamedBlock.blockItems();
        var blockHeader = blockItems.getFirst().getBlockHeader();
        var filename = BlockFile.getFilename(blockHeader.getNumber(), false);
        return new BlockStream(blockItems, null, filename, streamedBlock.loadStart(), null);
    }
}
