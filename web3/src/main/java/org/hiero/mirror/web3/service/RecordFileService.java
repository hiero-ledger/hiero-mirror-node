// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.viewmodel.BlockType;

public interface RecordFileService {

    /**
     * @param block the {@link BlockType} with the block number
     * @return the record file associated with the given block
     */
    Optional<RecordFile> findByBlockType(BlockType block);

    /**
     * @param timestamp the consensus timestamp of a transaction
     * @return the record file containing the transaction with the given timestamp
     */
    Optional<RecordFile> findByTimestamp(Long timestamp);
}
