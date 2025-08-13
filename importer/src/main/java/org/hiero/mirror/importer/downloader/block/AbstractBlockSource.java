// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
abstract class AbstractBlockSource implements BlockSource {

    protected final BlockStreamReader blockStreamReader;
    protected final BlockStreamVerifier blockStreamVerifier;
    protected final CommonDownloaderProperties commonDownloaderProperties;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final BlockProperties properties;

    @Override
    public final void get() {
        long nextBlockNumber = blockStreamVerifier.getNextBlockNumber();
        var endBlockNumber = commonDownloaderProperties.getImporterProperties().getEndBlockNumber();
        if (endBlockNumber != null && nextBlockNumber > endBlockNumber) {
            return;
        }

        doGet(nextBlockNumber, endBlockNumber);
    }

    protected abstract void doGet(long blockNumber, Long endBlockNumber);

    protected final BlockFile onBlockStream(BlockStream blockStream) {
        var blockFile = blockStreamReader.read(blockStream);
        if (!properties.isPersistBytes()) {
            blockFile.setBytes(null);
        }

        blockStreamVerifier.verify(blockFile);
        return blockFile;
    }
}
