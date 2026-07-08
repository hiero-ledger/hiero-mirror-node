// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
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

    private static final int ADJACENT_SLOTS_TO_CACHE = 3;
    private static final long NO_BLOCK_TIMESTAMP = -1L;
    private static final BigInteger UINT256_MAX = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
    private static final byte[] EMPTY_VALUE = new byte[0];

    private final CacheManager cacheManagerSlotsPerContract;
    private final CacheProperties cacheProperties;
    private final Cache contractSlotsCache;
    private final Cache contractStateCache;
    private final ContractStateRepository contractStateRepository;

    ContractStateServiceImpl(
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS) CacheManager cacheManagerContractSlots,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager cacheManagerContractState,
            final @Qualifier(CACHE_MANAGER_SLOTS_PER_CONTRACT) CacheManager cacheManagerSlotsPerContract,
            final CacheProperties cacheProperties,
            final ContractStateRepository contractStateRepository) {
        this.cacheManagerSlotsPerContract = cacheManagerSlotsPerContract;
        this.cacheProperties = cacheProperties;
        this.contractSlotsCache = cacheManagerContractSlots.getCache(CACHE_NAME);
        this.contractStateCache = cacheManagerContractState.getCache(CACHE_NAME);
        this.contractStateRepository = contractStateRepository;
    }

    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        return findStorage(contractId, key, NO_BLOCK_TIMESTAMP);
    }

    /**
     * Executes findStorageBatch query if the slot value is not cached.
     *
     * @param contractId     Entity ID of the contract that the slot key belongs to
     * @param key            The slot key of the slot value we are looking for
     * @param blockTimestamp consensus timestamp upper bound, or {@link #NO_BLOCK_TIMESTAMP} for latest state
     * @return slot value as 32-length left padded Bytes
     */
    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key, final long blockTimestamp) {
        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return blockTimestamp == NO_BLOCK_TIMESTAMP
                    ? contractStateRepository.findStorage(contractId.getId(), key)
                    : contractStateRepository.findStorageByBlockTimestamp(contractId.getId(), key, blockTimestamp);
        }

        final var cacheKey = blockTimestamp == NO_BLOCK_TIMESTAMP
                ? generateCacheKey(contractId, key)
                : generateHistoricalCacheKey(contractId, key, blockTimestamp);
        final var cachedValue = contractStateCache.get(cacheKey, byte[].class);

        if (cachedValue != null && cachedValue != EMPTY_VALUE) {
            return Optional.of(cachedValue);
        }

        final var slotsCache = blockTimestamp == NO_BLOCK_TIMESTAMP
                ? getSlotsCacheForContract(contractId)
                : getSlotsCacheForContractAtBlock(contractId, blockTimestamp);

        return findStorageBatchInternal(contractId, key, slotsCache, blockTimestamp);
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result. The goal of the
     * query is to preload previously requested data to avoid additional queries against the db. If the discovery pass
     * recorded slot keys for the contract, up to the first {@link CacheProperties#getMaxSlotKeysPerBatch()} of them (in
     * search order) are consumed from the context queue and loaded; otherwise the requested slot and its adjacent slots
     * are loaded.
     *
     * <p>When {@code blockTimestamp} equals {@link #NO_BLOCK_TIMESTAMP} the current state table is queried and cache
     * keys are {@code (contractId, slotKey)}. Otherwise the historical {@code contract_state_change} table is queried
     * up to that timestamp and cache keys are {@code (contractId, slotKey, blockTimestamp)}, keeping historical and
     * latest caches fully isolated.
     *
     * @param contractId     id of the contract
     * @param key            slot key being looked up
     * @param slotsCache     per-contract (or per-contract-per-block) slot tracker cache
     * @param blockTimestamp consensus timestamp upper bound, or {@link #NO_BLOCK_TIMESTAMP} for latest state
     * @return slot value if found
     */
    private Optional<byte[]> findStorageBatchInternal(
            final EntityId contractId, final byte[] key, final CaffeineCache slotsCache, final long blockTimestamp) {
        final var wrappedKey = ByteBuffer.wrap(key);

        Queue<byte[]> discoveredSlotKeys = null;
        if (ContractCallContext.isInitialized()) {
            final var ctx = ContractCallContext.get();
            // Only consume discovered keys when the current query's timestamp mode matches the mode under which the
            // discovery pass ran. Mixing historical discovered keys into a latest batch (or vice versa) causes the same
            // slot to be fetched twice — once with the real timestamp and once with NO_BLOCK_TIMESTAMP — producing
            // inconsistent results and unnecessary DB round-trips.
            final Long discoveryTs = ctx.getDiscoveryBlockTimestamp();
            final boolean timestampModeMatches = blockTimestamp == NO_BLOCK_TIMESTAMP
                    ? discoveryTs == null
                    : discoveryTs != null && discoveryTs == blockTimestamp;
            if (timestampModeMatches) {
                discoveredSlotKeys = ctx.getDiscoveredStorageSlotKeys(contractId);
            }
        }
        final var maxSlotKeysPerBatch = cacheProperties.getMaxSlotKeysPerBatch();
        if (discoveredSlotKeys != null && !discoveredSlotKeys.isEmpty()) {
            for (int i = 0; i < maxSlotKeysPerBatch; i++) {
                final var discoveredSlotKey = discoveredSlotKeys.poll();
                if (discoveredSlotKey == null) {
                    break;
                }
                slotsCache.putIfAbsent(ByteBuffer.wrap(discoveredSlotKey), EMPTY_VALUE);
            }
        } else {
            cacheSlotKeyAndAdjacentSlots(slotsCache, key);
        }

        slotsCache.putIfAbsent(wrappedKey, EMPTY_VALUE);

        final var cachedSlotKeys = slotsCache.getNativeCache().asMap().keySet();

        final var slotKeysToSearch = new ArrayList<byte[]>(Math.min(cachedSlotKeys.size(), maxSlotKeysPerBatch));
        var isKeyEvictedFromCache = true;
        var requestedKeyQueriedInBatch = false;
        for (var cachedSlotKey : cachedSlotKeys) {
            // Cap the batch at the first maxSlotKeysPerBatch slots to search; any remaining slots (including the
            // requested key, if beyond the cap) fall back to an individual query below.
            if (slotKeysToSearch.size() >= maxSlotKeysPerBatch) {
                break;
            }
            final var slotKeyBytes = ((ByteBuffer) cachedSlotKey).array();
            final var stateCacheKey = blockTimestamp == NO_BLOCK_TIMESTAMP
                    ? generateCacheKey(contractId, slotKeyBytes)
                    : generateHistoricalCacheKey(contractId, slotKeyBytes, blockTimestamp);
            // The cachedSlotKey already has a known value in the state cache. Skip searching in DB for this
            // cachedSlotKey.
            if (contractStateCache.get(stateCacheKey, byte[].class) != null) {
                if (wrappedKey.equals(cachedSlotKey)) {
                    isKeyEvictedFromCache = false;
                }
                continue;
            }
            if (wrappedKey.equals(cachedSlotKey)) {
                requestedKeyQueriedInBatch = true;
            }
            slotKeysToSearch.add(slotKeyBytes);
        }

        byte[] foundValue = null;
        if (!slotKeysToSearch.isEmpty()) {
            final var slots = slotKeysToSearch.toArray(byte[][]::new);
            log.info(
                    "findStorageBatch contractId={} slotKeys=[{}] timestamp={}",
                    contractId.getId(),
                    formatSlotKeysDecimal(slotKeysToSearch),
                    blockTimestamp);
            final var contractSlotValues = blockTimestamp == NO_BLOCK_TIMESTAMP
                    ? contractStateRepository.findStorageBatch(contractId.getId(), slots)
                    : contractStateRepository.findStorageBatchByBlockTimestamp(
                            contractId.getId(), slots, blockTimestamp);
            final var slotValuesByKey =
                    applyBatchSlotQueryResults(contractId, contractSlotValues, slotsCache, blockTimestamp);

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

    private CaffeineCache getSlotsCacheForContract(final EntityId contractId) {
        return (CaffeineCache)
                contractSlotsCache.get(contractId, () -> cacheManagerSlotsPerContract.getCache(contractId.toString()));
    }

    private CaffeineCache getSlotsCacheForContractAtBlock(final EntityId contractId, final long blockTimestamp) {
        final var cacheKey = new SimpleKey(contractId, blockTimestamp);
        return (CaffeineCache) contractSlotsCache.get(
                cacheKey, () -> cacheManagerSlotsPerContract.getCache(contractId + ":hist:" + blockTimestamp));
    }

    private Map<ByteBuffer, byte[]> applyBatchSlotQueryResults(
            final EntityId contractId,
            final List<ContractSlotValue> contractSlotValues,
            final Cache slotsCache,
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

    private void cacheSlotKeyAndAdjacentSlots(final Cache slotsCache, final byte[] key) {
        var slotKey = key;
        final var wrappedSlotKey = ByteBuffer.wrap(slotKey);
        slotsCache.putIfAbsent(wrappedSlotKey, EMPTY_VALUE);

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

    private SimpleKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId, slotKey);
    }

    private SimpleKey generateHistoricalCacheKey(
            final EntityId contractId, final byte[] slotKey, final long blockTimestamp) {
        return new SimpleKey(contractId, slotKey, blockTimestamp);
    }
}
