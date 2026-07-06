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
    private static final int INITIAL_SLOT_INDEXES_TO_CACHE = 100;
    private static final int MAX_SLOT_KEYS_PER_BATCH = 30;
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

        return findStorageBatch(contractId, key);
    }

    @Override
    public Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId.getId(), slotKeyByteArray, blockTimestamp);
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result. The goal of the
     * query is to preload previously requested data to avoid additional queries against the db. If the discovery pass
     * recorded slot keys for the contract, up to the first {@value #MAX_SLOT_KEYS_PER_BATCH} of them (in search order)
     * are consumed from the context queue and loaded; otherwise the requested slot and its adjacent slots are loaded.
     *
     * @param contractId id of the contract that the slotKey-value pairs are queried for.
     * @return slotKey-value pairs for contractId
     */
    private Optional<byte[]> findStorageBatch(final EntityId contractId, final byte[] key) {
        final var contractSlotsCacheForContract = getSlotsCacheForContract(contractId);
        final var searchedAbsentSlotsCacheForContract = getSearchedAbsentSlotsCacheForContract(contractId);
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
                contractSlotsCacheForContract.putIfAbsent(ByteBuffer.wrap(discoveredSlotKey), EMPTY_VALUE);
            }
        } else {
            cacheSlotKeyAndAdjacentSlots(contractSlotsCacheForContract, searchedAbsentSlotsCacheForContract, key);
        }

        contractSlotsCacheForContract.putIfAbsent(ByteBuffer.wrap(key), EMPTY_VALUE);

        final var cachedSlotKeys =
                contractSlotsCacheForContract.getNativeCache().asMap().keySet();

        final var slotKeysToSearch = new ArrayList<byte[]>(cachedSlotKeys.size());
        var isKeyEvictedFromCache = true;
        var requestedKeyQueriedInBatch = false;
        for (var slotKey : cachedSlotKeys) {
            final var slotKeyBytes = ((ByteBuffer) slotKey).array();
            // The slotKey is present in cache - it has an existing value or it was searched in DB, but no result for it
            // was returned. Skip searching in DB for this slotKey.
            if (contractStateCache.get(generateCacheKey(contractId, slotKeyBytes), byte[].class) != null
                    || searchedAbsentSlotsCacheForContract.get(slotKey) != null) {
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
            final var contractSlotValues = contractStateRepository.findStorageBatch(
                    contractId.getId(), slotKeysToSearch.toArray(byte[][]::new));
            final var slotValuesByKey = applyBatchSlotQueryResults(
                    contractId,
                    slotKeysToSearch,
                    contractSlotValues,
                    contractSlotsCacheForContract,
                    searchedAbsentSlotsCacheForContract);

            if (slotValuesByKey.containsKey(wrappedKey)) {
                foundValue = slotValuesByKey.get(wrappedKey);
            }
        }

        // If the cache key was evicted and hasn't been requested since, the cached value will be null.
        // In that case, fall back to the original query and cache the result so it survives slot cache eviction.
        if (isKeyEvictedFromCache && !requestedKeyQueriedInBatch) {
            final var value = contractStateRepository.findStorage(contractId.getId(), key);
            value.ifPresent(v -> contractStateCache.put(generateCacheKey(contractId, key), v));
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

    private Map<ByteBuffer, byte[]> applyBatchSlotQueryResults(
            final EntityId contractId,
            final Iterable<byte[]> queriedSlots,
            final List<ContractSlotValue> contractSlotValues,
            final Cache contractSlotsCacheForContract,
            final Cache searchedAbsentSlotsCacheForContract) {
        final var slotValuesByKey = toSlotValuesMap(contractSlotValues);

        for (final var entry : slotValuesByKey.entrySet()) {
            final var slotKey = entry.getKey().array();
            contractStateCache.put(generateCacheKey(contractId, slotKey), entry.getValue());
            // The value now lives in the contract state cache, so the slot key no longer needs to be tracked for batch
            // loading.
            contractSlotsCacheForContract.evictIfPresent(entry.getKey());
            searchedAbsentSlotsCacheForContract.evictIfPresent(entry.getKey());
        }

        for (final var slotKey : queriedSlots) {
            final var wrappedSlot = ByteBuffer.wrap(slotKey);
            if (!slotValuesByKey.containsKey(wrappedSlot)) {
                searchedAbsentSlotsCacheForContract.putIfAbsent(wrappedSlot, EMPTY_VALUE);
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

    private void cacheSlotKeyAndAdjacentSlots(
            final Cache contractSlotsCache, final Cache searchedAbsentSlotsCache, final byte[] key) {
        var slotKey = key;
        final var wrappedSlotKey = ByteBuffer.wrap(slotKey);
        contractSlotsCache.putIfAbsent(wrappedSlotKey, EMPTY_VALUE);
        searchedAbsentSlotsCache.evictIfPresent(wrappedSlotKey);

        for (int i = 0; i < ADJACENT_SLOTS_TO_CACHE; i++) {
            final var nextSlotKey = incrementSlotKey(slotKey);
            if (nextSlotKey == null) {
                break;
            }
            contractSlotsCache.putIfAbsent(ByteBuffer.wrap(nextSlotKey), EMPTY_VALUE);
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

    private String formatSlotKeysDecimal(final Iterable<byte[]> slots) {
        final var builder = new StringBuilder();
        var first = true;
        for (final var slot : slots) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(new BigInteger(1, slot));
            first = false;
        }
        return builder.toString();
    }
}
