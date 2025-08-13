// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.reader.block.BlockStream;

class Utils {
    private static final long MS_IN_NANOS = 1_000_000L;

    static long getLatency(BlockFile blockFile, BlockStream blockStream) {
        return blockStream.blockCompleteTime() - blockFile.getConsensusEnd() / MS_IN_NANOS;
    }
}
