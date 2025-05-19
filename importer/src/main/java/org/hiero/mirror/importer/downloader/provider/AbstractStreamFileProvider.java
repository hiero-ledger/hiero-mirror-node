// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.StreamType;
import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties;
import org.hiero.mirror.importer.downloader.CommonDownloaderProperties.PathType;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
abstract class AbstractStreamFileProvider implements StreamFileProvider {

    protected final CommonProperties commonProperties;
    protected final CommonDownloaderProperties downloaderProperties;

    @Override
    public final Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename) {
        if (streamFilename.getStreamType() == StreamType.BLOCK) {
            if (downloaderProperties.getPathType() != PathType.NODE_ID) {
                throw new IllegalStateException("Path type must be NODE_ID for block streams");
            }

            String filePath =
                    getBlockStreamFilePath(commonProperties.getShard(), node.getNodeId(), streamFilename.getFilename());
            streamFilename = StreamFilename.from(filePath);
        }

        return doGet(streamFilename);
    }

    protected abstract Mono<StreamFileData> doGet(StreamFilename streamFilename);

    protected abstract String getBlockStreamFilePath(long shard, long nodeId, String filename);
}
