// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.hiero.mirror.importer.downloader.block.scheduler.Scheduler.EARLIEST_AVAILABLE_BLOCK_NUMBER;

import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.scheduler.Scheduler;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
final class BlockNodeSubscriber extends AbstractBlockSource implements AutoCloseable {

    private final Scheduler scheduler;

    BlockNodeSubscriber(
            final BlockStreamReader blockStreamReader,
            final BlockStreamVerifier blockStreamVerifier,
            final CommonDownloaderProperties commonDownloaderProperties,
            final BlockProperties properties,
            final Supplier<Scheduler> schedulerSupplier) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, properties);
        scheduler = schedulerSupplier.get();
    }

    @Override
    public void close() {
        scheduler.close();
    }

    @Override
    protected void doGet(final long blockNumber, final Long endBlockNumber) {
        final var nextBlockNumber = new AtomicLong(blockNumber);
        final var node = scheduler.getNode(nextBlockNumber);
        if (blockNumber == EARLIEST_AVAILABLE_BLOCK_NUMBER && !shouldGetBlock(nextBlockNumber.get())) {
            return;
        }

        log.info("Start streaming block {} from {}", nextBlockNumber.get(), node);
        node.streamBlocks(
                nextBlockNumber.get(),
                endBlockNumber,
                this::handleBlockStream,
                commonDownloaderProperties.getTimeout());
    }

    private boolean handleBlockStream(final BlockStream blockStream, final String blockNode) {
        final var blockFile = onBlockStream(blockStream, blockNode);
        return scheduler.shouldReschedule(blockFile, blockStream);
    }
}
