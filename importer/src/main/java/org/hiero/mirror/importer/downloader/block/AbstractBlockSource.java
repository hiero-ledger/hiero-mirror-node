// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
abstract class AbstractBlockSource implements BlockSource {

    private static final long GENESIS_BLOCK_NUMBER = 0;

    protected final BlockStreamReader blockStreamReader;
    protected final BlockStreamVerifier blockStreamVerifier;
    protected final CommonDownloaderProperties commonDownloaderProperties;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final BlockProperties properties;

    protected final long getNextBlockNumber() {
        return blockStreamVerifier
                .getLastBlockFile()
                .map(BlockFile::getIndex)
                .map(v -> v + 1)
                .or(() -> Optional.ofNullable(
                        commonDownloaderProperties.getImporterProperties().getStartBlockNumber()))
                .orElse(GENESIS_BLOCK_NUMBER);
    }

    protected final BlockFile onBlockStream(BlockStream blockStream) {
        var blockFile = blockStreamReader.read(blockStream);
        if (!properties.isPersistBytes()) {
            blockFile.setBytes(null);
        }

        blockStreamVerifier.verify(blockFile);
        return blockFile;
    }
}
