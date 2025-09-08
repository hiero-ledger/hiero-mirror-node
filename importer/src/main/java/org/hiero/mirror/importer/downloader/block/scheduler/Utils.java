// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.reader.block.BlockStream;

final class Utils {

    private static final long MS_IN_NANOS = 1_000_000L;

    /**
     * Measures the block streaming latency, defined as the time difference (in millis) between the block's consensus
     * end and the receipt of its last streaming response.
     *
     * @param blockFile The block file
     * @param blockStream The block stream
     * @return Latency in millis
     */
    static long getLatency(BlockFile blockFile, BlockStream blockStream) {
        return blockStream.blockCompleteTime() - blockFile.getConsensusEnd() / MS_IN_NANOS;
    }
}
