// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_EARLIEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_INDEX;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_LATEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_TIMESTAMP;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_RECORD_FILE_LATEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_RECORD_FILE_LATEST_INDEX;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RecordFileRepository extends PagingAndSortingRepository<RecordFile, Long> {

    @Cacheable(
            cacheNames = CACHE_NAME_RECORD_FILE_LATEST_INDEX,
            cacheManager = CACHE_MANAGER_RECORD_FILE_LATEST,
            unless = "#result == null")
    @Query("select max(r.index) from RecordFile r")
    Optional<Long> findLatestIndex();

    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_RECORD_FILE_EARLIEST, unless = "#result == null")
    @Query(value = "select * from record_file order by index asc limit 1", nativeQuery = true)
    Optional<RecordFile> findEarliest();

    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_RECORD_FILE_INDEX, unless = "#result == null")
    @Query("select r from RecordFile r where r.index = ?1")
    Optional<RecordFile> findByIndex(long index);

    @Cacheable(
            cacheNames = CACHE_NAME_RECORD_FILE_LATEST,
            cacheManager = CACHE_MANAGER_RECORD_FILE_LATEST,
            unless = "#result == null")
    @Query(value = "select * from record_file order by consensus_end desc limit 1", nativeQuery = true)
    Optional<RecordFile> findLatest();

    @Caching(
            cacheable =
                    @Cacheable(
                            cacheNames = CACHE_NAME,
                            cacheManager = CACHE_MANAGER_RECORD_FILE_TIMESTAMP,
                            unless = "#result == null"),
            put = @CachePut(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_RECORD_FILE_INDEX))
    @Query("select r from RecordFile r where r.consensusEnd >= ?1 order by r.consensusEnd asc limit 1")
    Optional<RecordFile> findByTimestamp(long timestamp);

    @Query(value = "select * from record_file where index >= ?1 and index <= ?2 order by index asc", nativeQuery = true)
    List<RecordFile> findByIndexRange(long startIndex, long endIndex);
}
