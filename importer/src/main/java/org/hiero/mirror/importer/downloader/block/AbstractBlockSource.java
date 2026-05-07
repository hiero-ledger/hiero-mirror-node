// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.block.cutover.CutoverService;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
@RequiredArgsConstructor
abstract class AbstractBlockSource implements BlockSource {

    protected final BlockStreamReader blockStreamReader;
    protected final BlockStreamVerifier blockStreamVerifier;
    protected final CommonDownloaderProperties commonDownloaderProperties;
    protected final CutoverService cutoverService;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final BlockProperties properties;

    @Override
    public final void get() {
        final long blockNumber = cutoverService.getNextBlockNumber();
        final var endBlockNumber =
                commonDownloaderProperties.getImporterProperties().getEndBlockNumber();
        if (shouldGetBlock(blockNumber, endBlockNumber)) {
            doGet(blockNumber, endBlockNumber);
        }
    }

    protected static boolean shouldGetBlock(final long blockNumber, final @Nullable Long endBlockNumber) {
        return endBlockNumber == null || blockNumber <= endBlockNumber;
    }

    protected abstract void doGet(final long blockNumber, final Long endBlockNumber);

    protected final BlockFile onBlockStream(final BlockStream blockStream, final String blockNode) {
        var blockFile = blockStreamReader.read(blockStream);
        if (!properties.isPersistBytes()) {
            blockFile.setBytes(null);

            if (blockFile.hasRecordFile()) {
                blockFile.getRecordFile().setBytes(null);
            }
        }

        blockFile.setNode(blockNode);
        blockStreamVerifier.verify(blockFile);
        return blockFile;
    }
}
