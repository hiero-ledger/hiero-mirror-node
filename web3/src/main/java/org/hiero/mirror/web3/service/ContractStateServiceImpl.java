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
 * {@link CacheLoader#loadAll} with {@code findStorageBatch} so multiple slot values are loaded and cached together.
 *
 * <p>The value cache is built asynchronously and used through its synchronous view because only the asynchronous
 * {@code getAll} reserves a placeholder per key before loading. That gives single-flight coalescing, so concurrent
 * misses of the same slot share one batch query.
 */
@Service
final class ContractStateServiceImpl implements ContractStateService {

    private static final byte[] EMPTY_VALUE = new byte[0];

    private final CacheProperties cacheProperties;
    private final Cache<EntityId, List<ByteBuffer>> contractSlotsCache;
    private final LoadingCache<ContractStateCacheKey, byte[]> contractStateCache;
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
        return contractStateRepository.findStorageByBlockTimestamp(entityId.getId(), slotKeyByteArray, blockTimestamp);
    }

    private byte[] loadContractSlot(final ContractStateCacheKey key) {
        return contractStateRepository.findStorage(key.contractId(), key.slot()).orElse(EMPTY_VALUE);
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
                final var slot = toByteArray(cachedContractSlot);
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
                result.put(ContractStateCacheKey.of(contractIdForBatchLoading, toByteArray(searchedSlot)), EMPTY_VALUE);
            }
        }

        // Guarantee every requested key has a mapping so getAll can return it.
        for (final var key : keys) {
            result.computeIfAbsent(key, this::loadContractSlot);
        }

        return result;
    }

    /**
     * Records slot keys searched in the DB for a contract so later lookups can prefetch them together.
     */
    private void trackSearchedSlots(final long contractId, final Collection<ByteBuffer> slotKeys) {
        final var entityId = EntityId.of(contractId);
        final var maxSlotsPerContract = cacheProperties.getMaxSlotsPerContract();
        contractSlotsCache.asMap().compute(entityId, (_, existing) -> {
            final var updated = new ArrayList<ByteBuffer>(
                    Math.min(maxSlotsPerContract, (existing == null ? 0 : existing.size()) + slotKeys.size()));
            if (existing != null) {
                updated.addAll(existing);
            }
            for (final var slotKey : slotKeys) {
                if (!updated.contains(slotKey)) {
                    updated.add(slotKey);
                }
            }
            if (updated.size() > maxSlotsPerContract) {
                return List.copyOf(updated.subList(updated.size() - maxSlotsPerContract, updated.size()));
            }
            return List.copyOf(updated);
        });
    }

    private static byte[][] toByteArray(final Collection<ByteBuffer> slots) {
        final var slotKeys = new byte[slots.size()][];
        var index = 0;
        for (final var slot : slots) {
            slotKeys[index++] = toByteArray(slot);
        }
        return slotKeys;
    }

    /**
     * Copies the buffer's readable bytes so sliced/duplicated buffers do not expose a larger backing array.
     */
    private static byte[] toByteArray(final ByteBuffer buffer) {
        final var view = buffer.asReadOnlyBuffer();
        final var bytes = new byte[view.remaining()];
        view.get(bytes);
        return bytes;
    }

    private static Optional<byte[]> toOptional(final byte[] cachedValue) {
        if (cachedValue == null || cachedValue == EMPTY_VALUE) {
            return Optional.empty();
        }
        return Optional.of(cachedValue);
    }
}
