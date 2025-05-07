// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.importer.downloader.BlockStreamSource;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.reader.block.BlockStream;
import org.hiero.mirror.importer.reader.block.BlockStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
abstract class AbstractBlockStreamSource implements BlockStreamSource {

    private static final long GENESIS_BLOCK_NUMBER = 0;

    protected final BlockStreamReader blockStreamReader;
    protected final BlockStreamVerifier blockStreamVerifier;
    protected final CommonDownloaderProperties commonDownloaderProperties;
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final BlockStreamProperties properties;

    protected final long getNextBlockNumber() {
        return blockStreamVerifier
                .getLastBlockNumber()
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
