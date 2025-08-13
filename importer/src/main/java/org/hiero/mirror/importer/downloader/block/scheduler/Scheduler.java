// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import org.hiero.mirror.common.domain.transaction.BlockFile;
import org.hiero.mirror.importer.downloader.block.BlockNode;
import org.hiero.mirror.importer.reader.block.BlockStream;

public interface Scheduler extends AutoCloseable {

    void close();

    BlockNode getNode(long blockNumber);

    default boolean shouldRescheduleOnBlockProcessed(BlockFile blockFile, BlockStream blockStream) {
        return false;
    }
}
