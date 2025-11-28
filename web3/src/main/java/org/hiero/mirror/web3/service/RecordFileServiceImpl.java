// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.exception.BlockNumberNotFoundException.UNKNOWN_BLOCK_NUMBER;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.exception.BlockNumberOutOfRangeException;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@CustomLog
@RequiredArgsConstructor
public class RecordFileServiceImpl implements RecordFileService {

    private final RecordFileRepository recordFileRepository;
    private final Web3Properties web3Properties;

    private final AtomicReference<RecordFile> latestRecord = new AtomicReference<>();
    private final AtomicLong latestIndex = new AtomicLong(-1);

    @Scheduled(fixedDelayString = "#{@web3Properties.getFrequency().toMillis()}")
    public void fetchLatestRecordFile() {
        if (!web3Properties.isSchedulerEnabled()) {
            return;
        }
        try {
            recordFileRepository.findLatest().ifPresent(record -> {
                latestRecord.set(record);
                latestIndex.set(record.getIndex());
            });
        } catch (Exception e) {
            log.error("Failed to refresh latest block", e);
        }
    }

    @Override
    public Optional<RecordFile> findByBlockType(BlockType block) {
        if (block == BlockType.EARLIEST) {
            return recordFileRepository.findEarliest();
        }
        if (block == BlockType.LATEST) {
            final var cached = latestRecord.get();
            if (cached != null) {
                return Optional.of(cached);
            }
            return recordFileRepository.findLatest();
        }

        long cachedIndex = latestIndex.get();
        if (cachedIndex < 0) {
            cachedIndex = recordFileRepository
                    .findLatestIndex()
                    .orElseThrow(() -> new BlockNumberOutOfRangeException(UNKNOWN_BLOCK_NUMBER));
            latestIndex.set(cachedIndex);
        }

        if (block.number() > cachedIndex) {
            throw new BlockNumberOutOfRangeException(UNKNOWN_BLOCK_NUMBER);
        }
        return recordFileRepository.findByIndex(block.number());
    }

    @Override
    public Optional<RecordFile> findByTimestamp(Long timestamp) {
        return recordFileRepository.findByTimestamp(timestamp);
    }
}
