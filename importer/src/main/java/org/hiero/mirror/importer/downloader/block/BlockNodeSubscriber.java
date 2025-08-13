// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.inject.Named;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.scheduler.Scheduler;
import org.hiero.mirror.importer.downloader.block.scheduler.SchedulerFactory;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;

@Named
final class BlockNodeSubscriber extends AbstractBlockSource implements AutoCloseable {

    private final Scheduler scheduler;

    BlockNodeSubscriber(
            BlockStreamReader blockStreamReader,
            BlockStreamVerifier blockStreamVerifier,
            CommonDownloaderProperties commonDownloaderProperties,
            BlockProperties properties,
            SchedulerFactory schedulerFactory) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, properties);
        scheduler = schedulerFactory.getScheduler(
                properties.getNodes(), properties.getScheduling().getType());
    }

    @Override
    public void close() {
        scheduler.close();
    }

    @Override
    protected void doGet(long blockNumber, Long endBlockNumber) {
        var node = scheduler.getNode(blockNumber);
        log.info("Start streaming block {} from {}", blockNumber, node);
        node.streamBlocks(
                blockNumber, endBlockNumber, this::handleBlockStream, commonDownloaderProperties.getTimeout());
    }

    private boolean handleBlockStream(BlockStream blockStream) {
        var blockFile = onBlockStream(blockStream);
        return scheduler.shouldRescheduleOnBlockProcessed(blockFile, blockStream);
    }
}
