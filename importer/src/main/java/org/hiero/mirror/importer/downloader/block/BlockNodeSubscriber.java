// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.hiero.mirror.importer.downloader.block.scheduler.Scheduler.EARLIEST_AVAILABLE_BLOCK_NUMBER;

import jakarta.inject.Named;
import java.util.function.Supplier;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
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
            final CutoverService cutoverService,
            final BlockProperties properties,
            final Supplier<Scheduler> schedulerSupplier) {
        super(blockStreamReader, blockStreamVerifier, commonDownloaderProperties, cutoverService, properties);
        scheduler = schedulerSupplier.get();
    }

    @Override
    public void close() {
        scheduler.close();
    }

    @Override
    protected void doGet(final long blockNumber, final Long endBlockNumber) {
        final var scheduled = scheduler.getNode(blockNumber);
        if (blockNumber == EARLIEST_AVAILABLE_BLOCK_NUMBER
                && !shouldGetBlock(scheduled.nextBlockNumber(), endBlockNumber)) {
            return;
        }

        final var node = scheduled.blockNode();
        log.info("Start streaming block {} from {}", scheduled.nextBlockNumber(), node);
        node.streamBlocks(
                scheduled.nextBlockNumber(),
                endBlockNumber,
                this::handleBlockStream,
                commonDownloaderProperties.getTimeout());
    }

    private boolean handleBlockStream(final BlockStream blockStream, final String blockNode) {
        final var blockFile = onBlockStream(blockStream, blockNode);
        return scheduler.shouldReschedule(blockFile, blockStream);
    }
}
