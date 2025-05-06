// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.provider;

import org.hiero.mirror.importer.addressbook.ConsensusNode;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.domain.StreamFilename;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A stream file provider abstracts away the source of stream files provided by consensus nodes.
 */
public interface StreamFileProvider {

    /**
     * Fetches a stream file from a particular node upon subscription.
     * @param node           the consensus node to download from
     * @param streamFilename the stream filename to download
     * @return the downloaded stream file data, wrapped in a Mono
     */
    Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename);

    /**
     * Lists and downloads signature files for a particular node upon subscription. Uses the provided lastFilename to
     * search for files lexicographically and chronologically after the last confirmed stream file.
     *
     * @param node         the consensus node to search
     * @param lastFilename the filename of the last downloaded stream file
     * @return The data associated with one or more stream files, wrapped in a Flux
     */
    Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename);
}
