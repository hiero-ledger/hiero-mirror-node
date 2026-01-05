// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
@RequiredArgsConstructor
abstract class AbstractBlockSource implements BlockSource {

    private static final long GENESIS_BLOCK_NUMBER = 0;

    protected static final long EARLIEST_AVAILABLE_BLOCK_NUMBER = -1;

    protected final BlockStreamReader blockStreamReader;
    protected final BlockStreamVerifier blockStreamVerifier;
    protected final CommonDownloaderProperties commonDownloaderProperties;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final BlockProperties properties;

    @Override
    public final void get() {
        final long blockNumber = getNextBlockNumber();
        if (shouldGetBlock(blockNumber)) {
            doGet(blockNumber);
        }
    }

    protected abstract void doGet(final long blockNumber);

    protected final BlockFile onBlockStream(final BlockStream blockStream, final String blockNodeEndpoint) {
        var blockFile = blockStreamReader.read(blockStream);
        if (!properties.isPersistBytes()) {
            blockFile.setBytes(null);
        }
        blockFile.setNode(blockNodeEndpoint);
        blockStreamVerifier.verify(blockFile);
        return blockFile;
    }

    protected final boolean shouldGetBlock(final long blockNumber) {
        final var endBlockNumber =
                commonDownloaderProperties.getImporterProperties().getEndBlockNumber();
        return endBlockNumber == null || blockNumber <= endBlockNumber;
    }

    private long getNextBlockNumber() {
        return blockStreamVerifier
                .getLastBlockFile()
                .map(BlockFile::getIndex)
                .map(v -> v + 1)
                .or(() -> Optional.ofNullable(
                        commonDownloaderProperties.getImporterProperties().getStartBlockNumber()))
                .orElse(GENESIS_BLOCK_NUMBER);
    }
}
