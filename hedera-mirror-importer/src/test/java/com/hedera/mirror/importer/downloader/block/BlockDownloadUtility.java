// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.downloader.block;

import com.hedera.mirror.common.domain.transaction.BlockFile;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.addressbook.ConsensusNode;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import com.hedera.mirror.importer.downloader.provider.StreamFileProvider;
import com.hedera.mirror.importer.downloader.record.RecordFileDownloader;
import com.hedera.mirror.importer.reader.block.BlockFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
public class BlockDownloadUtility {

    private final BlockFileReader blockFileReader;
    private final CommonDownloaderProperties commonDownloaderProperties;
    private final StreamFileProvider streamFileProvider;
    private final RecordFileReader recordFileReader;
    private final RecordFileDownloader recordFileDownloader;

    BlockFile downloadBlock(ConsensusNode node, StreamFilename streamFilename) {
        commonDownloaderProperties.setPathType(PathType.NODE_ID);
        // Set to block specific path prefix
        //commonDownloaderProperties.setPathPrefix("block-preview/previewnet-2025-02-07T18:17");
        commonDownloaderProperties.setPathPrefix("block-preview/testnet-2024-12-03T17:27");
        var blockFileData = streamFileProvider
                .get(node, streamFilename)
                .blockOptional(commonDownloaderProperties.getTimeout())
                .orElseThrow();
        log.info("Downloaded block file {}", blockFileData.getFilename());
        return blockFileReader.read(blockFileData);
    }

    RecordFile downloadRecordFile(ConsensusNode node, String previous) {
        commonDownloaderProperties.setPathType(PathType.ACCOUNT_ID);
        commonDownloaderProperties.setPathPrefix(null);
        var recordFileData = streamFileProvider
                .list(node, StreamFilename.from("recordstreams/record0.0.3/" + previous))
                .take(1).blockLast();
        log.info("Downloaded record file {}", recordFileData.getFilename());
        var recordFile = recordFileReader.read(recordFileData);
        recordFileDownloader.downloadSidecars(StreamFilename.from("recordstreams/record0.0.3/" + recordFileData.getFilename()), recordFile, node);
        return recordFile;
    }
}
