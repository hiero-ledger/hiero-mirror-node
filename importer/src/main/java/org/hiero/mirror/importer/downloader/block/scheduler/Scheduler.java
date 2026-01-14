// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.reader.block.BlockStream;

public interface Scheduler extends AutoCloseable {

    long EARLIEST_AVAILABLE_BLOCK_NUMBER = -1;

    void close();

    /**
     * Selects a block node to stream blocks from starting from the specified block number
     *
     * @param blockNumber The block number of the first block to stream. Set to -1 to start from the earliest block
     * @return The block node, or null if none can provide the block
     */
    BlockNode getNode(AtomicLong blockNumber);

    /**
     * Checks if block node rescheduling is needed given a processed {@link BlockFile} and the {@link BlockStream}
     *
     * @param blockFile The processed {@link BlockFile}
     * @param blockStream The {@link BlockStream}
     * @return True if rescheduling is needed, otherwise false
     */
    default boolean shouldReschedule(BlockFile blockFile, BlockStream blockStream) {
        return false;
    }
}
