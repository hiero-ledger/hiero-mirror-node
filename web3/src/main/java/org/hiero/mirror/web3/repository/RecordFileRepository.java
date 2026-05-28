// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_EARLIEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_INDEX;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_LATEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_TIMESTAMP;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_RECORD_FILE_LATEST;

import java.util.List;
import java.util.Optional;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface RecordFileRepository extends PagingAndSortingRepository<RecordFile, Long> {

    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_RECORD_FILE_EARLIEST, unless = "#result == null")
    @Query(value = "select * from record_file order by index asc limit 1")
    Optional<RecordFile> findEarliest();

    @Cacheable(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_RECORD_FILE_INDEX, unless = "#result == null")
    @Query("select * from record_file where index = :index")
    Optional<RecordFile> findByIndex(long index);

    @Query("select * from record_file where hash like concat(:hash, '%')")
    Optional<RecordFile> findByHash(String hash);

    @Cacheable(
            cacheNames = CACHE_NAME_RECORD_FILE_LATEST,
            cacheManager = CACHE_MANAGER_RECORD_FILE_LATEST,
            unless = "#result == null")
    @Query(value = "select * from record_file order by consensus_end desc limit 1")
    Optional<RecordFile> findLatest();

    @Caching(
            cacheable =
                    @Cacheable(
                            cacheNames = CACHE_NAME,
                            cacheManager = CACHE_MANAGER_RECORD_FILE_TIMESTAMP,
                            unless = "#result == null"),
            put = @CachePut(cacheNames = CACHE_NAME, cacheManager = CACHE_MANAGER_RECORD_FILE_INDEX))
    @Query("select * from record_file where consensus_end >= :timestamp order by consensus_end asc limit 1")
    Optional<RecordFile> findByTimestamp(long timestamp);

    @Query(value = "select * from record_file where index >= :startIndex and index <= :endIndex order by index asc")
    List<RecordFile> findByIndexRange(long startIndex, long endIndex);
}
