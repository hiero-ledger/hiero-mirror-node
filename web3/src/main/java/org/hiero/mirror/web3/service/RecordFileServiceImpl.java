// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.exception.BlockNumberOutOfRangeException;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecordFileServiceImpl implements RecordFileService {

    private final RecordFileRepository recordFileRepository;

    @Override
    public Optional<RecordFile> findByBlockType(BlockType block) {
        if (block == BlockType.EARLIEST) {
            return recordFileRepository.findEarliest();
        } else if (block == BlockType.LATEST) {
            return recordFileRepository.findLatest();
        }

        long latestBlock = recordFileRepository
                .findLatestIndex()
                .orElseThrow(() -> new BlockNumberOutOfRangeException(UNKNOWN_BLOCK_NUMBER));

        if (block.number() > latestBlock) {
            throw new BlockNumberOutOfRangeException(UNKNOWN_BLOCK_NUMBER);
        }
        return recordFileRepository.findByIndex(block.number());
    }

    @Override
    public Optional<RecordFile> findByTimestamp(Long timestamp) {
        return recordFileRepository.findByTimestamp(timestamp);
    }
}
