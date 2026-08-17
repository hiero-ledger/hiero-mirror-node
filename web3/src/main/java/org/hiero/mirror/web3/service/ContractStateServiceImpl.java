// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.EqualsAndHashCode.CacheStrategy;
import lombok.Value;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
final class ContractStateServiceImpl implements ContractStateService {

    /**
     * Negative cache sentinel for a slot known to have no row. Slot values are never zero length.
     */
    private static final byte[] EMPTY_VALUE = new byte[0];

    private final CacheProperties cacheProperties;
    private final Cache contractSlotsCache;
    private final Cache contractStateCache;
    private final ContractStateRepository contractStateRepository;
    private final CaffeineSpec slotsPerContractSpec;

    ContractStateServiceImpl(
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS) CacheManager cacheManagerContractSlots,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager cacheManagerContractState,
            final CacheProperties cacheProperties,
            final ContractStateRepository contractStateRepository) {
        this.cacheProperties = cacheProperties;
        this.contractSlotsCache = cacheManagerContractSlots.getCache(CACHE_NAME);
        this.contractStateCache = cacheManagerContractState.getCache(CACHE_NAME);
        this.contractStateRepository = contractStateRepository;
        this.slotsPerContractSpec = CaffeineSpec.parse(cacheProperties.getSlotsPerContract());
    }

    /**
     * Returns the slot value, loading it from the database together with other slots previously searched for the same
     * contract when it is not cached.
     *
     * @param contractId Entity ID of the contract that the slot key belongs to
     * @param key        The slot key of the slot value we are looking for
     * @return slot value as 32-length left padded Bytes
     */
    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        final var cacheKey = generateCacheKey(contractId, key);
        final var cachedValue = contractStateCache.get(cacheKey, byte[].class);

        if (cachedValue != null) {
            return toOptional(cachedValue);
        }

        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return findStorageSingle(contractId, key, cacheKey);
        }

        return findStorageBatch(contractId, key);
    }

    @Override
    public Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId.getId(), slotKeyByteArray, blockTimestamp);
    }

    /**
     * Loads a single slot. Caffeine computes a given key at most once, so concurrent callers for the same slot share
     * one query instead of each opening a connection.
     */
    private Optional<byte[]> findStorageSingle(final EntityId contractId, final byte[] key, final SlotKey cacheKey) {
        return toOptional(contractStateCache.get(cacheKey, () -> contractStateRepository
                .findStorage(contractId.getId(), key)
                .orElse(EMPTY_VALUE)));
    }

    /**
     * Executes a batch query returning slotKey-value pairs for the contract, then caches the result. The goal is to
     * preload previously searched slots to avoid additional queries against the database.
     *
     * <p>Only slots without a cached value are queried and the batch is capped, so the query does not grow with the
     * accumulated key set. Slots with no row are cached as {@link #EMPTY_VALUE} so they stop re-triggering a batch.
     */
    private Optional<byte[]> findStorageBatch(final EntityId contractId, final byte[] key) {
        final var slotKeys = slotKeys(contractId);
        final var wrappedKey = new Bytes(key);
        slotKeys.put(wrappedKey, Boolean.TRUE);

        final int maxSlotKeysPerBatch = cacheProperties.getMaxSlotKeysPerBatch();
        final var slots = new byte[(int) Math.max(1, Math.min(maxSlotKeysPerBatch, slotKeys.estimatedSize()))][];

        // The requested key is always queried, even if another thread cached it after the miss above, so its value is
        // never absent from the result.
        slots[0] = key;
        int size = 1;

        for (final var slotKey : slotKeys.asMap().keySet()) {
            if (size == slots.length) {
                break;
            }

            if (slotKey.equals(wrappedKey)) {
                continue;
            }

            final var slot = slotKey.getValue();
            if (contractStateCache.get(generateCacheKey(contractId, slot)) == null) {
                slots[size++] = slot;
            }
        }

        final var minimalSlots = slots.length == size ? slots : Arrays.copyOf(slots, size);
        final var contractSlotValues = contractStateRepository.findStorageBatch(contractId.getId(), minimalSlots);
        final var foundSlots = HashSet.<Bytes>newHashSet(contractSlotValues.size());
        byte[] value = null;

        for (final var contractSlotValue : contractSlotValues) {
            final var slot = contractSlotValue.getSlot();
            final var slotValue = contractSlotValue.getValue();
            contractStateCache.put(generateCacheKey(contractId, slot), slotValue);
            foundSlots.add(new Bytes(slot));

            if (Arrays.equals(slot, key)) {
                value = slotValue;
            }
        }

        for (int i = 0; i < size; i++) {
            if (!foundSlots.contains(new Bytes(slots[i]))) {
                contractStateCache.put(generateCacheKey(contractId, slots[i]), EMPTY_VALUE);
            }
        }

        return toOptional(value);
    }

    /**
     * Returns the bounded set of slot keys previously searched for the contract. The per-contract cache is the value
     * of the entry, so it becomes unreachable when the entry is evicted rather than being retained for the lifetime of
     * the process by a cache manager.
     */
    private com.github.benmanes.caffeine.cache.Cache<Bytes, Boolean> slotKeys(final EntityId contractId) {
        return contractSlotsCache.get(
                contractId, () -> Caffeine.from(slotsPerContractSpec).build());
    }

    // Generates a cache key emulating the default caching behavior in Spring
    private SlotKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SlotKey(contractId.getId(), slotKey);
    }

    private static Optional<byte[]> toOptional(final byte[] value) {
        return value == null || value.length == 0 ? Optional.empty() : Optional.of(value);
    }

    @EqualsAndHashCode(cacheStrategy = CacheStrategy.LAZY)
    @Value
    static class Bytes {
        private final byte[] value;
    }

    @EqualsAndHashCode(cacheStrategy = CacheStrategy.LAZY)
    @Value
    private class SlotKey {
        private final long contractId;
        private final byte[] slot;
    }
}
