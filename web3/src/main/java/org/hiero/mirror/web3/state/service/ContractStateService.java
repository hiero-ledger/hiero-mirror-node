// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;

@CustomLog
@Named
public class ContractStateService {

    private final ContractStateRepository contractStateRepository;
    private final Cache contractStateCache;
    private final Cache contractSlotsCache;
    private final CacheProperties cacheProperties;

    protected ContractStateService(
            final ContractStateRepository contractStateRepository,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager contractStateCacheManager,
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS) CacheManager contractSlotCacheManager,
            final CacheProperties cacheProperties) {
        this.contractStateRepository = contractStateRepository;
        this.contractStateCache =
                requireNonNull(contractStateCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.contractSlotsCache =
                requireNonNull(contractSlotCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.cacheProperties = cacheProperties;
    }

    /**
     * Executes findStorageBatch query if the slot value is not cached.
     * @param contractId entity id of the contract that the slot key belongs to
     * @param key the slot key of the slot value we are looking for
     * @return slot value
     */
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        final var cacheKey = generateCacheKey(contractId, key);
        final var cachedValue = contractStateCache.get(cacheKey);
        if (cachedValue != null) {
            return unwrapCacheValue(cachedValue);
        }

        return findStorageBatch(contractId, key);
    }

    public Optional<byte[]> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp);
    }

    /**
     * Adds the given slot key to the tracked cache for the specified contract if it isn't already present,
     * and then performs a batch query for all tracked slot keys. The results are used to populate the
     * contract state cache for both existing and non-existing slot values.
     * <p>
     * Uses concurrent data structures to ensure thread safety without blocking concurrent requests.
     *
     * @param contractId the ID of the contract whose slot keys are being managed and queried
     * @param key the specific slot key to add to the tracked set and fetch from storage if not cached
     */
    private Optional<byte[]> findStorageBatch(final EntityId contractId, final byte[] key) {
        final String encodedSlotKey = encodeSlotKey(key);

        SlotTracker slotTracker = getOrCreateSlotTracker(contractId);

        slotTracker.addSlot(encodedSlotKey);

        // Get snapshot of current slots for querying (thread-safe)
        final List<String> currentSlots = slotTracker.getCurrentSlots();
        final List<byte[]> cachedSlots = new ArrayList<>(currentSlots.size());
        for (String slotKey : currentSlots) {
            cachedSlots.add(decodeSlotKey(slotKey));
        }

        final var existingSlotKeyValuePairs = contractStateRepository.findStorageBatch(contractId.getId(), cachedSlots);

        byte[] cachedValue = null;
        Set<ByteBuffer> existingSlots = new HashSet<>();

        for (int i = 0; i < existingSlotKeyValuePairs.size(); i++) {
            final var contractSlotValue = existingSlotKeyValuePairs.get(i);
            final byte[] slotKey = contractSlotValue.getSlot();
            final byte[] slotValue = contractSlotValue.getValue();
            existingSlots.add(ByteBuffer.wrap(slotKey));
            if (java.util.Arrays.equals(slotKey, key)) {
                cachedValue = slotValue;
            }
        }
        cacheSlotValues(contractId, existingSlotKeyValuePairs);

        final List<byte[]> nonExistingSlots = new ArrayList<>();
        for (byte[] slot : cachedSlots) {
            if (!existingSlots.contains(ByteBuffer.wrap(slot))) {
                nonExistingSlots.add(slot);
            }
        }
        cacheNonExistingSlots(contractId, nonExistingSlots);

        return cachedValue == null ? Optional.empty() : Optional.of(cachedValue);
    }

    /**
     * Gets or creates a thread-safe slot tracker for a given contract.
     */
    private SlotTracker getOrCreateSlotTracker(final EntityId contractId) {
        return contractSlotsCache.get(contractId, () -> new SlotTracker(cacheProperties.getCachedSlotsMaxSize()));
    }

    /**
     * Caches the slot values returned as part of the findStorageBatch query.
     * @param contractId id of the contract that the slot values will be cached for
     * @param slotKeyValuePairs the slot key value pairs returned from the findStorageBatch query
     */
    private void cacheSlotValues(final EntityId contractId, final List<ContractSlotValue> slotKeyValuePairs) {
        for (ContractSlotValue slotKeyValuePair : slotKeyValuePairs) {
            final byte[] slotKey = slotKeyValuePair.getSlot();
            final byte[] slotValue = slotKeyValuePair.getValue();
            if (slotValue != null) {
                contractStateCache.put(generateCacheKey(contractId, slotKey), Optional.of(slotValue));
            }
        }
    }

    /**
     * Caches the slot keys that were passed to a findStorage/findStorageBatch query, but were not present in the db.
     * @param contractId id of the contract that the slot keys were queried for
     * @param nonExistingSlots the slot keys queried for the contractId that were not present in the db
     */
    private void cacheNonExistingSlots(final EntityId contractId, final List<byte[]> nonExistingSlots) {
        for (byte[] slot : nonExistingSlots) {
            contractStateCache.put(generateCacheKey(contractId, slot), Optional.empty());
        }
    }

    /**
     * Encodes slotKey in base64 - the format the slot keys are written in the cache
     * @param slotKey - the slot key stored as a byte array in the database
     * @return base64 encoded slot key
     */
    private String encodeSlotKey(final byte[] slotKey) {
        return Base64.getEncoder().encodeToString(slotKey);
    }

    /**
     * Decodes base64 slot key to byte array
     * @param slotKeyBase64 base64 encoded slot key
     * @return slot key as byte array
     */
    private byte[] decodeSlotKey(final String slotKeyBase64) {
        return Base64.getDecoder().decode(slotKeyBase64);
    }

    /**
     * Generates a cache key emulating the default caching behavior in Spring
     * @param contractId id of the contract that the slot key belongs to
     * @param slotKey slot key as byte array
     * @return SimpleKey Object used as cache key in the findStorage cache for example
     */
    private Object generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId, slotKey);
    }

    @SuppressWarnings("unchecked")
    private Optional<byte[]> unwrapCacheValue(ValueWrapper cachedValue) {
        return (Optional<byte[]>) cachedValue.get();
    }

    /**
     * Thread-safe LRU slot tracker using concurrent data structures.
     * Avoids synchronized maps and unsafe iteration patterns.
     */
    private static class SlotTracker {
        private final ConcurrentHashMap<String, Boolean> slots = new ConcurrentHashMap<>();
        private final ConcurrentLinkedDeque<String> insertionOrder = new ConcurrentLinkedDeque<>();
        private final int maxSize;

        public SlotTracker(int maxSize) {
            this.maxSize = maxSize;
        }

        public void addSlot(String slot) {
            // Only add if not already present
            if (slots.putIfAbsent(slot, Boolean.TRUE) == null) {
                insertionOrder.addLast(slot);

                // Remove oldest entries if over limit
                while (slots.size() > maxSize) {
                    String oldest = insertionOrder.pollFirst();
                    if (oldest != null) {
                        slots.remove(oldest);
                    }
                }
            }
        }

        public List<String> getCurrentSlots() {
            return new ArrayList<>(slots.keySet());
        }

        public int size() {
            return slots.size();
        }
    }
}
