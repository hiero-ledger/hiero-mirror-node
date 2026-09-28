// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_EARLIEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_HASH;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_INDEX;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_LATEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_TIMESTAMP;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_RECORD_FILE_LATEST;

import com.github.benmanes.caffeine.cache.Cache;
import java.util.Optional;
import java.util.function.Function;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Service;

@Service
public class RecordFileServiceImpl implements RecordFileService {

    /**
     * The earliest and latest caches hold a single record file each, so their sole entry needs a constant key.
     */
    private static final Object SINGLE_ENTRY_KEY = new Object();

    private final CachedLookup<Object> earliestLookup;
    private final CachedLookup<String> hashLookup;
    private final CachedLookup<Long> indexLookup;
    private final CachedLookup<Object> latestLookup;
    private final CachedLookup<Long> timestampLookup;

    public RecordFileServiceImpl(
            final RecordFileRepository recordFileRepository,
            @Qualifier(CACHE_MANAGER_RECORD_FILE_EARLIEST) final CacheManager earliestCacheManager,
            @Qualifier(CACHE_MANAGER_RECORD_FILE_HASH) final CacheManager hashCacheManager,
            @Qualifier(CACHE_MANAGER_RECORD_FILE_INDEX) final CacheManager indexCacheManager,
            @Qualifier(CACHE_MANAGER_RECORD_FILE_LATEST) final CacheManager latestCacheManager,
            @Qualifier(CACHE_MANAGER_RECORD_FILE_TIMESTAMP) final CacheManager timestampCacheManager) {
        this.earliestLookup = new CachedLookup<>(
                nativeCache(earliestCacheManager, CACHE_NAME), loadAndIndex(_ -> recordFileRepository.findEarliest()));
        this.hashLookup = new CachedLookup<>(
                nativeCache(hashCacheManager, CACHE_NAME), loadAndIndex(recordFileRepository::findByHash));
        this.indexLookup = new CachedLookup<>(
                nativeCache(indexCacheManager, CACHE_NAME),
                index -> recordFileRepository.findByIndex(index).orElse(null));
        this.latestLookup = new CachedLookup<>(
                nativeCache(latestCacheManager, CACHE_NAME_RECORD_FILE_LATEST),
                loadAndIndex(_ -> recordFileRepository.findLatest()));
        this.timestampLookup = new CachedLookup<>(
                nativeCache(timestampCacheManager, CACHE_NAME), loadAndIndex(recordFileRepository::findByTimestamp));
    }

    @Override
    public Optional<RecordFile> findByBlockType(BlockType block) {
        if (block == BlockType.EARLIEST) {
            return Optional.ofNullable(earliestLookup.get(SINGLE_ENTRY_KEY));
        } else if (block == BlockType.LATEST) {
            return Optional.ofNullable(latestLookup.get(SINGLE_ENTRY_KEY));
        } else if (block.isHash()) {
            // The block.name() format is already validated by BlockType.of()
            return Optional.ofNullable(hashLookup.get(block.name()));
        }

        return findByIndex(block.number());
    }

    @Override
    public Optional<RecordFile> findByIndex(long index) {
        return Optional.ofNullable(indexLookup.get(index));
    }

    @Override
    public Optional<RecordFile> findByTimestamp(Long timestamp) {
        return Optional.ofNullable(timestampLookup.get(timestamp));
    }

    /**
     * Wraps a record file query as a cache loader that also caches the loaded record file under its index. Record files
     * never change once ingested, so one loaded by any other key can serve later block number lookups (e.g. BLOCKHASH).
     * Returns null when there is no such record file, which Caffeine takes as "do not record an entry".
     */
    private <K> Function<K, RecordFile> loadAndIndex(final Function<K, Optional<RecordFile>> query) {
        return key -> query.apply(key)
                .map(recordFile -> {
                    indexLookup.put(recordFile.getIndex(), recordFile);
                    return recordFile;
                })
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static <K> Cache<K, RecordFile> nativeCache(final CacheManager cacheManager, final String cacheName) {
        final var cache = cacheManager.getCache(cacheName);
        if (!(cache instanceof CaffeineCache caffeineCache)) {
            throw new IllegalStateException("Expected a Caffeine cache named " + cacheName);
        }

        return (Cache<K, RecordFile>) (Cache<?, ?>) caffeineCache.getNativeCache();
    }

    /**
     * A record file cache paired with the loader that populates it. The loader returns null when there is no such
     * record file, which Caffeine takes as "do not record an entry".
     */
    private record CachedLookup<K>(Cache<K, RecordFile> cache, Function<K, RecordFile> loader) {

        RecordFile get(final K key) {
            return cache.get(key, loader);
        }

        void put(final K key, final RecordFile recordFile) {
            cache.put(key, recordFile);
        }
    }
}
