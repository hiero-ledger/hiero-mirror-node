// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.springframework.stereotype.Service;

/**
 * Loads contract storage with batch prefetch and negative caching ({@code EMPTY_VALUE}). Bulk misses use
 * {@link CacheLoader#loadAll} with {@code findStorageBatch} / {@code findStorageBatchByBlockTimestamp} so multiple slot
 * values are loaded and cached together.
 *
 * <p>The value caches are built asynchronously and used through their synchronous view because only the asynchronous
 * {@code getAll} reserves a placeholder per key before loading. That gives single-flight coalescing, so concurrent
 * misses of the same slot share one batch query.
 */
@Service
final class ContractStateServiceImpl implements ContractStateService {

    private static final byte[] EMPTY_VALUE = new byte[0];

    private final CacheProperties cacheProperties;
    private final Cache<EntityId, List<ByteBuffer>> contractSlotsCache;
    private final LoadingCache<ContractStateCacheKey, byte[]> contractStateCache;
    private final LoadingCache<ContractStateHistoricalCacheKey, byte[]> contractStateHistoricalCache;
    private final ContractStateRepository contractStateRepository;

    ContractStateServiceImpl(
            final CacheProperties cacheProperties, final ContractStateRepository contractStateRepository) {
        this.cacheProperties = cacheProperties;
        this.contractSlotsCache =
                Caffeine.from(cacheProperties.getContractSlots()).build();
        this.contractStateRepository = contractStateRepository;
        this.contractStateCache = Caffeine.from(cacheProperties.getContractState())
                // Load on the calling thread so the query keeps the caller's transaction and request scoped values.
                .executor(Runnable::run)
                .buildAsync(new CacheLoader<ContractStateCacheKey, byte[]>() {
                    @Override
                    public byte[] load(final ContractStateCacheKey key) {
                        return loadContractSlot(key);
                    }

                    @Override
                    public Map<ContractStateCacheKey, byte[]> loadAll(final Set<? extends ContractStateCacheKey> keys) {
                        return loadContractSlots(keys);
                    }
                })
                .synchronous();
        this.contractStateHistoricalCache = Caffeine.from(cacheProperties.getContractStateHistorical())
                .executor(Runnable::run)
                .buildAsync(new CacheLoader<ContractStateHistoricalCacheKey, byte[]>() {
                    @Override
                    public byte[] load(final ContractStateHistoricalCacheKey key) {
                        return loadContractSlotHistorical(key);
                    }

                    @Override
                    public Map<ContractStateHistoricalCacheKey, byte[]> loadAll(
                            final Set<? extends ContractStateHistoricalCacheKey> keys) {
                        return loadContractSlotsHistorical(keys);
                    }
                })
                .synchronous();
    }

    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        final var cacheKey = ContractStateCacheKey.of(contractId.getId(), key);
        final var cachedValue = contractStateCache.getIfPresent(cacheKey);
        if (cachedValue != null) {
            return toOptional(cachedValue);
        }

        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return toOptional(contractStateCache.get(cacheKey));
        }

        ContractCallContext.get().setContractIdForStorageCache(contractId);
        return toOptional(contractStateCache.getAll(Set.of(cacheKey)).get(cacheKey));
    }

    @Override
    public Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        final var cacheKey = ContractStateHistoricalCacheKey.of(entityId.getId(), slotKeyByteArray, blockTimestamp);
        final var cachedValue = contractStateHistoricalCache.getIfPresent(cacheKey);
        if (cachedValue != null) {
            return toOptional(cachedValue);
        }

        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return toOptional(contractStateHistoricalCache.get(cacheKey));
        }

        ContractCallContext.get().setContractIdForStorageCache(entityId);
        return toOptional(contractStateHistoricalCache.getAll(Set.of(cacheKey)).get(cacheKey));
    }

    private byte[] loadContractSlot(final ContractStateCacheKey key) {
        return contractStateRepository.findStorage(key.contractId(), key.slot()).orElse(EMPTY_VALUE);
    }

    private byte[] loadContractSlotHistorical(final ContractStateHistoricalCacheKey key) {
        return contractStateRepository
                .findStorageByBlockTimestamp(key.contractId(), key.slot(), key.timestamp())
                .orElse(EMPTY_VALUE);
    }

    /**
     * Loads all requested keys and returns a value for every key ({@link #EMPTY_VALUE} when missing). Prefetches
     * previously searched slots of the same contract that are not already cached.
     */
    private Map<ContractStateCacheKey, byte[]> loadContractSlots(final Set<? extends ContractStateCacheKey> keys) {
        final var contractEntityIdForBatchLoading = ContractCallContext.get().getContractIdForStorageCache();
        final var contractIdForBatchLoading = contractEntityIdForBatchLoading.getId();
        final var cachedContractSlots = contractSlotsCache.getIfPresent(contractEntityIdForBatchLoading);
        final var maxSlotKeysPerBatch = cacheProperties.getMaxSlotKeysPerBatch();

        // ByteBuffer keys give content-based equality so the same slot is not queried twice.
        final var slotsToSearch = LinkedHashSet.<ByteBuffer>newLinkedHashSet(maxSlotKeysPerBatch);

        // Always include the requested keys first so they are never dropped by the batch cap.
        for (final var key : keys) {
            if (key.contractId() == contractIdForBatchLoading) {
                slotsToSearch.add(ByteBuffer.wrap(key.slot()));
            }
        }

        if (cachedContractSlots != null) {
            for (final var cachedContractSlot : cachedContractSlots) {
                if (slotsToSearch.size() >= maxSlotKeysPerBatch) {
                    break;
                }
                final var slot = cachedContractSlot.array();
                if (contractStateCache.getIfPresent(ContractStateCacheKey.of(contractIdForBatchLoading, slot))
                        == null) {
                    slotsToSearch.add(cachedContractSlot);
                }
            }
        }

        final var slotKeys = toByteArray(slotsToSearch);
        final var contractSlotValues = contractStateRepository.findStorageBatch(contractIdForBatchLoading, slotKeys);
        trackSearchedSlots(contractIdForBatchLoading, slotsToSearch);
        final var foundSlots = HashSet.<ByteBuffer>newHashSet(contractSlotValues.size());
        final var result = HashMap.<ContractStateCacheKey, byte[]>newHashMap(slotsToSearch.size());

        for (final var contractSlotValue : contractSlotValues) {
            final var slotKey = contractSlotValue.getSlot();
            foundSlots.add(ByteBuffer.wrap(slotKey));
            result.put(ContractStateCacheKey.of(contractIdForBatchLoading, slotKey), contractSlotValue.getValue());
        }

        for (final var searchedSlot : slotsToSearch) {
            if (!foundSlots.contains(searchedSlot)) {
                result.put(ContractStateCacheKey.of(contractIdForBatchLoading, searchedSlot.array()), EMPTY_VALUE);
            }
        }

        // Guarantee every requested key has a mapping so getAll can return it.
        for (final var key : keys) {
            result.computeIfAbsent(key, this::loadContractSlot);
        }

        return result;
    }

    /**
     * Loads all requested historical keys and returns a value for every key ({@link #EMPTY_VALUE} when missing).
     * Prefetches previously searched slots of the same contract that are not already cached at the same timestamp.
     */
    private Map<ContractStateHistoricalCacheKey, byte[]> loadContractSlotsHistorical(
            final Set<? extends ContractStateHistoricalCacheKey> keys) {
        final var contractEntityIdForBatchLoading = ContractCallContext.get().getContractIdForStorageCache();
        final var contractIdForBatchLoading = contractEntityIdForBatchLoading.getId();
        final var timestamp = keys.iterator().next().timestamp();
        final var cachedContractSlots = contractSlotsCache.getIfPresent(contractEntityIdForBatchLoading);
        final var maxSlotKeysPerBatch = cacheProperties.getMaxSlotKeysPerBatch();

        final var slotsToSearch = LinkedHashSet.<ByteBuffer>newLinkedHashSet(maxSlotKeysPerBatch);

        for (final var key : keys) {
            if (key.contractId() == contractIdForBatchLoading && key.timestamp() == timestamp) {
                slotsToSearch.add(ByteBuffer.wrap(key.slot()));
            }
        }

        if (cachedContractSlots != null) {
            for (final var cachedContractSlot : cachedContractSlots) {
                if (slotsToSearch.size() >= maxSlotKeysPerBatch) {
                    break;
                }
                final var slot = cachedContractSlot.array();
                if (contractStateHistoricalCache.getIfPresent(
                                ContractStateHistoricalCacheKey.of(contractIdForBatchLoading, slot, timestamp))
                        == null) {
                    slotsToSearch.add(cachedContractSlot);
                }
            }
        }

        final var slotKeys = toByteArray(slotsToSearch);
        final var contractSlotValues = contractStateRepository.findStorageBatchByBlockTimestamp(
                contractIdForBatchLoading, slotKeys, timestamp);
        trackSearchedSlots(contractIdForBatchLoading, slotsToSearch);
        final var foundSlots = HashSet.<ByteBuffer>newHashSet(contractSlotValues.size());
        final var result = HashMap.<ContractStateHistoricalCacheKey, byte[]>newHashMap(slotsToSearch.size());

        for (final var contractSlotValue : contractSlotValues) {
            final var slotKey = contractSlotValue.getSlot();
            foundSlots.add(ByteBuffer.wrap(slotKey));
            result.put(
                    ContractStateHistoricalCacheKey.of(contractIdForBatchLoading, slotKey, timestamp),
                    contractSlotValue.getValue());
        }

        for (final var searchedSlot : slotsToSearch) {
            if (!foundSlots.contains(searchedSlot)) {
                result.put(
                        ContractStateHistoricalCacheKey.of(contractIdForBatchLoading, searchedSlot.array(), timestamp),
                        EMPTY_VALUE);
            }
        }

        for (final var key : keys) {
            result.computeIfAbsent(key, this::loadContractSlotHistorical);
        }

        return result;
    }

    /**
     * Records slot keys searched in the DB for a contract so later lookups can prefetch them together.
     */
    private void trackSearchedSlots(final long contractId, final Collection<ByteBuffer> slotKeys) {
        final var entityId = EntityId.of(contractId);
        final var maxSlotsPerContract = cacheProperties.getMaxSlotsPerContract();
        final var existing = contractSlotsCache.getIfPresent(entityId);
        final var updated = new ArrayList<ByteBuffer>(
                Math.min(maxSlotsPerContract, (existing == null ? 0 : existing.size()) + slotKeys.size()));
        if (existing != null) {
            updated.addAll(existing);
        }
        for (final var slotKey : slotKeys) {
            if (updated.size() > maxSlotsPerContract) {
                updated.removeFirst();
            }

            updated.add(slotKey);
        }

        contractSlotsCache.put(entityId, updated);
    }

    private static byte[][] toByteArray(final Collection<ByteBuffer> slots) {
        final var slotKeys = new byte[slots.size()][];
        var index = 0;
        for (final var slot : slots) {
            slotKeys[index++] = slot.array();
        }
        return slotKeys;
    }

    private static Optional<byte[]> toOptional(final byte[] cachedValue) {
        if (cachedValue == null || cachedValue == EMPTY_VALUE) {
            return Optional.empty();
        }
        return Optional.of(cachedValue);
    }
}
