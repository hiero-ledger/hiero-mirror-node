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
import java.util.function.Consumer;
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
        final Consumer<RecordFile> onLoad = this::cacheByAllKeys;
        this.earliestLookup = new CachedLookup<>(
                nativeCache(earliestCacheManager, CACHE_NAME), _ -> recordFileRepository.findEarliest(), onLoad);
        this.hashLookup =
                new CachedLookup<>(nativeCache(hashCacheManager, CACHE_NAME), recordFileRepository::findByHash, onLoad);
        this.indexLookup = new CachedLookup<>(
                nativeCache(indexCacheManager, CACHE_NAME), recordFileRepository::findByIndex, onLoad);
        this.latestLookup = new CachedLookup<>(
                nativeCache(latestCacheManager, CACHE_NAME_RECORD_FILE_LATEST),
                _ -> recordFileRepository.findLatest(),
                onLoad);
        this.timestampLookup = new CachedLookup<>(
                nativeCache(timestampCacheManager, CACHE_NAME), recordFileRepository::findByTimestamp, onLoad);
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
     * Warms the full hash, index and consensus-end timestamp keys after each successful database load, never on a
     * cache hit. Hash prefixes and other transaction timestamps are cached under their requested key when queried.
     * Hash and index entries expire after write; timestamp entries expire after access.
     */
    private void cacheByAllKeys(final RecordFile recordFile) {
        cacheByHash(recordFile);
        cacheByIndex(recordFile);
        cacheByTimestamp(recordFile);
    }

    private void cacheByHash(final RecordFile recordFile) {
        hashLookup.put(recordFile.getHash(), recordFile);
    }

    private void cacheByIndex(final RecordFile recordFile) {
        indexLookup.put(recordFile.getIndex(), recordFile);
    }

    private void cacheByTimestamp(final RecordFile recordFile) {
        timestampLookup.put(recordFile.getConsensusEnd(), recordFile);
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
     * A record file cache paired with its query and a callback run once per successful load. The callback runs after
     * the cache entry is computed, never inside the mapping function: writing to another cache from there would hold
     * this cache's entry lock while waiting on the other's, and two concurrent misses on the same record file by
     * different keys (e.g. hash and index) could deadlock. Other callers can read the source entry before warming
     * finishes, so they may still miss another cache. Empty results and failures leave no cached entry, so callers
     * waiting on the same key may each retry the query instead of sharing that result.
     */
    private record CachedLookup<K>(
            Cache<K, RecordFile> cache, Function<K, Optional<RecordFile>> query, Consumer<RecordFile> onLoad) {

        RecordFile get(final K key) {
            final var loaded = new RecordFile[1];
            final var recordFile =
                    cache.get(key, k -> loaded[0] = query.apply(k).orElse(null));
            if (loaded[0] != null) {
                onLoad.accept(loaded[0]);
            }

            return recordFile;
        }

        void put(final K key, final RecordFile recordFile) {
            cache.put(key, recordFile);
        }
    }
}
