// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_INDEX;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class RecordFileServiceImpl implements RecordFileService {

    private final Cache indexCache;
    private final RecordFileRepository recordFileRepository;

    public RecordFileServiceImpl(
            final RecordFileRepository recordFileRepository,
            @Qualifier(CACHE_MANAGER_RECORD_FILE_INDEX) final CacheManager indexCacheManager) {
        this.recordFileRepository = recordFileRepository;
        this.indexCache = indexCacheManager.getCache(CACHE_NAME);
    }

    @Override
    public Optional<RecordFile> findByBlockType(BlockType block) {
        if (block == BlockType.EARLIEST) {
            return recordFileRepository.findEarliest();
        } else if (block == BlockType.LATEST) {
            return recordFileRepository.findLatest();
        } else if (block.isHash()) {
            // The block.name() format is already validated by BlockType.of()
            return recordFileRepository.findByHash(block.name());
        }

        return recordFileRepository.findByIndex(block.number());
    }

    @Override
    public Optional<RecordFile> findByTimestamp(Long timestamp) {
        final var recordFile = recordFileRepository.findByTimestamp(timestamp);
        recordFile.ifPresent(file -> indexCache.put(file.getIndex(), file));
        return recordFile;
    }
}
