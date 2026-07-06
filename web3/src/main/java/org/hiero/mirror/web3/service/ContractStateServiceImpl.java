// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SEARCHED_ABSENT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.stereotype.Service;

@CustomLog
@Service
final class ContractStateServiceImpl implements ContractStateService {

    private static final int ADJACENT_SLOTS_TO_CACHE = 6;
    private static final int MAX_SLOT_KEYS_PER_BATCH = 30;
    private static final long NO_BLOCK_TIMESTAMP = -1L;
    private static final BigInteger UINT256_MAX = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
    private static final byte[] EMPTY_VALUE = new byte[0];

    private final CacheManager cacheManagerSlotsPerContract;
    private final CacheProperties cacheProperties;
    private final Cache contractSlotsCache;
    private final Cache contractStateCache;
    private final Cache searchedAbsentSlotsCache;
    private final ContractStateRepository contractStateRepository;

    ContractStateServiceImpl(
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS) CacheManager cacheManagerContractSlots,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager cacheManagerContractState,
            final @Qualifier(CACHE_MANAGER_SEARCHED_ABSENT_SLOTS) CacheManager cacheManagerSearchedAbsentSlots,
            final @Qualifier(CACHE_MANAGER_SLOTS_PER_CONTRACT) CacheManager cacheManagerSlotsPerContract,
            final CacheProperties cacheProperties,
            final ContractStateRepository contractStateRepository) {
        this.cacheManagerSlotsPerContract = cacheManagerSlotsPerContract;
        this.cacheProperties = cacheProperties;
        this.contractSlotsCache = cacheManagerContractSlots.getCache(CACHE_NAME);
        this.contractStateCache = cacheManagerContractState.getCache(CACHE_NAME);
        this.searchedAbsentSlotsCache = cacheManagerSearchedAbsentSlots.getCache(CACHE_NAME);
        this.contractStateRepository = contractStateRepository;
    }

    /**
     * Executes findStorageBatch query if the slot value is not cached.
     *
     * @param contractId Entity ID of the contract that the slot key belongs to
     * @param key        The slot key of the slot value we are looking for
     * @return slot value as 32-length left padded Bytes
     */
    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return contractStateRepository.findStorage(contractId.getId(), key);
        }

        final var cachedValue = contractStateCache.get(generateCacheKey(contractId, key), byte[].class);

        if (cachedValue != null && cachedValue != EMPTY_VALUE) {
            return Optional.of(cachedValue);
        }

        return findStorageBatchInternal(
                contractId,
                key,
                getSlotsCacheForContract(contractId),
                getSearchedAbsentSlotsCacheForContract(contractId),
                NO_BLOCK_TIMESTAMP);
    }

    @Override
    public Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return contractStateRepository.findStorageByBlockTimestamp(
                    entityId.getId(), slotKeyByteArray, blockTimestamp);
        }

        final var cachedValue = contractStateCache.get(
                generateHistoricalCacheKey(entityId, slotKeyByteArray, blockTimestamp), byte[].class);

        if (cachedValue != null && cachedValue != EMPTY_VALUE) {
            return Optional.of(cachedValue);
        }

        return findStorageBatchInternal(
                entityId,
                slotKeyByteArray,
                getSlotsCacheForContractAtBlock(entityId, blockTimestamp),
                getSearchedAbsentSlotsCacheForContractAtBlock(entityId, blockTimestamp),
                blockTimestamp);
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result. The goal of the
     * query is to preload previously requested data to avoid additional queries against the db. If the discovery pass
     * recorded slot keys for the contract, up to the first {@value #MAX_SLOT_KEYS_PER_BATCH} of them (in search order)
     * are consumed from the context queue and loaded; otherwise the requested slot and its adjacent slots are loaded.
     *
     * <p>When {@code blockTimestamp} equals {@link #NO_BLOCK_TIMESTAMP} the current state table is queried and cache
     * keys are {@code (contractId, slotKey)}. Otherwise the historical {@code contract_state_change} table is queried
     * up to that timestamp and cache keys are {@code (contractId, slotKey, blockTimestamp)}, keeping historical and
     * latest caches fully isolated.
     *
     * @param contractId       id of the contract
     * @param key              slot key being looked up
     * @param slotsCache       per-contract (or per-contract-per-block) slot tracker cache
     * @param absentSlotsCache per-contract (or per-contract-per-block) absent slot tracker cache
     * @param blockTimestamp   consensus timestamp upper bound, or {@link #NO_BLOCK_TIMESTAMP} for latest state
     * @return slot value if found
     */
    private Optional<byte[]> findStorageBatchInternal(
            final EntityId contractId,
            final byte[] key,
            final CaffeineCache slotsCache,
            final CaffeineCache absentSlotsCache,
            final long blockTimestamp) {
        final var wrappedKey = ByteBuffer.wrap(key);

        final var discoveredSlotKeys = ContractCallContext.isInitialized()
                ? ContractCallContext.get().getDiscoveredStorageSlotKeys(contractId)
                : null;
        if (discoveredSlotKeys != null && !discoveredSlotKeys.isEmpty()) {
            for (int i = 0; i < MAX_SLOT_KEYS_PER_BATCH; i++) {
                final var discoveredSlotKey = discoveredSlotKeys.poll();
                if (discoveredSlotKey == null) {
                    break;
                }
                slotsCache.putIfAbsent(ByteBuffer.wrap(discoveredSlotKey), EMPTY_VALUE);
            }
        } else {
            cacheSlotKeyAndAdjacentSlots(slotsCache, absentSlotsCache, key);
        }

        slotsCache.putIfAbsent(wrappedKey, EMPTY_VALUE);

        final var cachedSlotKeys = slotsCache.getNativeCache().asMap().keySet();

        final var slotKeysToSearch = new ArrayList<byte[]>(cachedSlotKeys.size());
        var isKeyEvictedFromCache = true;
        var requestedKeyQueriedInBatch = false;
        for (var slotKey : cachedSlotKeys) {
            final var slotKeyBytes = ((ByteBuffer) slotKey).array();
            final var stateCacheKey = blockTimestamp == NO_BLOCK_TIMESTAMP
                    ? generateCacheKey(contractId, slotKeyBytes)
                    : generateHistoricalCacheKey(contractId, slotKeyBytes, blockTimestamp);
            // The slotKey is present in cache - it has an existing value or it was searched in DB, but no result for it
            // was returned. Skip searching in DB for this slotKey.
            if (contractStateCache.get(stateCacheKey, byte[].class) != null || absentSlotsCache.get(slotKey) != null) {
                if (wrappedKey.equals(slotKey)) {
                    isKeyEvictedFromCache = false;
                }
                continue;
            }
            if (wrappedKey.equals(slotKey)) {
                requestedKeyQueriedInBatch = true;
            }
            slotKeysToSearch.add(slotKeyBytes);
        }

        byte[] foundValue = null;
        if (!slotKeysToSearch.isEmpty()) {
            final var slots = slotKeysToSearch.toArray(byte[][]::new);
            final var contractSlotValues = blockTimestamp == NO_BLOCK_TIMESTAMP
                    ? contractStateRepository.findStorageBatch(contractId.getId(), slots)
                    : contractStateRepository.findStorageBatchByBlockTimestamp(
                            contractId.getId(), slots, blockTimestamp);
            final var slotValuesByKey = applyBatchSlotQueryResults(
                    contractId, slotKeysToSearch, contractSlotValues, slotsCache, absentSlotsCache, blockTimestamp);

            if (slotValuesByKey.containsKey(wrappedKey)) {
                foundValue = slotValuesByKey.get(wrappedKey);
            }
        }

        // If the cache key was evicted and hasn't been requested since, the cached value will be null.
        // In that case, fall back to the original query and cache the result so it survives slot cache eviction.
        if (isKeyEvictedFromCache && !requestedKeyQueriedInBatch) {
            final Optional<byte[]> value;
            if (blockTimestamp == NO_BLOCK_TIMESTAMP) {
                value = contractStateRepository.findStorage(contractId.getId(), key);
                value.ifPresent(v -> contractStateCache.put(generateCacheKey(contractId, key), v));
            } else {
                value = contractStateRepository.findStorageByBlockTimestamp(contractId.getId(), key, blockTimestamp);
                value.ifPresent(
                        v -> contractStateCache.put(generateHistoricalCacheKey(contractId, key, blockTimestamp), v));
            }
            return value;
        }

        return Optional.ofNullable(foundValue);
    }

    private CaffeineCache getSlotsCacheForContract(final EntityId contractId) {
        return (CaffeineCache)
                contractSlotsCache.get(contractId, () -> cacheManagerSlotsPerContract.getCache(contractId.toString()));
    }

    private CaffeineCache getSearchedAbsentSlotsCacheForContract(final EntityId contractId) {
        return (CaffeineCache) searchedAbsentSlotsCache.get(
                contractId, () -> cacheManagerSlotsPerContract.getCache(contractId + ":searchedAbsent"));
    }

    private CaffeineCache getSlotsCacheForContractAtBlock(final EntityId contractId, final long blockTimestamp) {
        final var cacheKey = new SimpleKey(contractId, blockTimestamp);
        return (CaffeineCache) contractSlotsCache.get(
                cacheKey, () -> cacheManagerSlotsPerContract.getCache(contractId + ":hist:" + blockTimestamp));
    }

    private CaffeineCache getSearchedAbsentSlotsCacheForContractAtBlock(
            final EntityId contractId, final long blockTimestamp) {
        final var cacheKey = new SimpleKey(contractId, blockTimestamp);
        return (CaffeineCache) searchedAbsentSlotsCache.get(
                cacheKey,
                () -> cacheManagerSlotsPerContract.getCache(
                        contractId + ":hist:" + blockTimestamp + ":searchedAbsent"));
    }

    private Map<ByteBuffer, byte[]> applyBatchSlotQueryResults(
            final EntityId contractId,
            final Iterable<byte[]> queriedSlots,
            final List<ContractSlotValue> contractSlotValues,
            final Cache slotsCache,
            final Cache absentSlotsCache,
            final long blockTimestamp) {
        final var slotValuesByKey = toSlotValuesMap(contractSlotValues);

        for (final var entry : slotValuesByKey.entrySet()) {
            final var slotKey = entry.getKey().array();
            final var cacheKey = blockTimestamp == NO_BLOCK_TIMESTAMP
                    ? generateCacheKey(contractId, slotKey)
                    : generateHistoricalCacheKey(contractId, slotKey, blockTimestamp);
            contractStateCache.put(cacheKey, entry.getValue());
            // The value now lives in the contract state cache, so the slot key no longer needs to be tracked for batch
            // loading.
            slotsCache.evictIfPresent(entry.getKey());
            absentSlotsCache.evictIfPresent(entry.getKey());
        }

        for (final var slotKey : queriedSlots) {
            final var wrappedSlot = ByteBuffer.wrap(slotKey);
            if (!slotValuesByKey.containsKey(wrappedSlot)) {
                absentSlotsCache.putIfAbsent(wrappedSlot, EMPTY_VALUE);
            }
        }

        return slotValuesByKey;
    }

    private Map<ByteBuffer, byte[]> toSlotValuesMap(final List<ContractSlotValue> contractSlotValues) {
        final Map<ByteBuffer, byte[]> slotValuesByKey = HashMap.newHashMap(contractSlotValues.size());
        for (final var contractSlotValue : contractSlotValues) {
            slotValuesByKey.put(ByteBuffer.wrap(contractSlotValue.getSlot()), contractSlotValue.getValue());
        }
        return slotValuesByKey;
    }

    private void cacheSlotKeyAndAdjacentSlots(final Cache slotsCache, final Cache absentSlotsCache, final byte[] key) {
        var slotKey = key;
        final var wrappedSlotKey = ByteBuffer.wrap(slotKey);
        slotsCache.putIfAbsent(wrappedSlotKey, EMPTY_VALUE);
        absentSlotsCache.evictIfPresent(wrappedSlotKey);

        for (int i = 0; i < ADJACENT_SLOTS_TO_CACHE; i++) {
            final var nextSlotKey = incrementSlotKey(slotKey);
            if (nextSlotKey == null) {
                break;
            }
            slotsCache.putIfAbsent(ByteBuffer.wrap(nextSlotKey), EMPTY_VALUE);
            slotKey = nextSlotKey;
        }
    }

    private byte[] incrementSlotKey(final byte[] key) {
        final var value = new BigInteger(1, key);
        if (value.equals(UINT256_MAX)) {
            return null;
        }
        return toPaddedSlotKey(value.add(BigInteger.ONE));
    }

    private byte[] toPaddedSlotKey(final BigInteger value) {
        final var bytes = value.toByteArray();
        final var result = new byte[32];
        final var offset = Math.max(0, bytes.length - 32);
        final var length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, offset, result, 32 - length, length);
        return result;
    }

    // Generates a cache key emulating the default caching behavior in Spring
    private SimpleKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId, slotKey);
    }

    // Generates a cache key that incorporates the block timestamp to isolate historical state from latest
    private SimpleKey generateHistoricalCacheKey(
            final EntityId contractId, final byte[] slotKey, final long blockTimestamp) {
        return new SimpleKey(contractId, slotKey, blockTimestamp);
    }
}
