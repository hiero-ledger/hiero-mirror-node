// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_MAX_CONTRACT_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.SimpleKey;

@CustomLog
@Named
public class ContractStateService {

    private final ContractStateRepository contractStateRepository;
    private final Cache contractStateCache;
    private final Cache contractSlotsCache;
    private final CacheManager cacheManager;
    private final CacheProperties cacheProperties;

    protected ContractStateService(
            final ContractStateRepository contractStateRepository,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager contractStateCacheManager,
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS) CacheManager contractSlotCacheManager,
            final @Qualifier(CACHE_MANAGER_MAX_CONTRACT_SLOTS_PER_CONTRACT) CacheManager cacheManager,
            final CacheProperties cacheProperties) {
        this.contractStateRepository = contractStateRepository;
        this.contractStateCache =
                requireNonNull(contractStateCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.contractSlotsCache =
                requireNonNull(contractSlotCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.cacheManager = cacheManager;
        this.cacheProperties = cacheProperties;
    }

    /**
     * Executes findStorageBatch query if the slot value is not cached.
     *
     * @param contractId entity id of the contract that the slot key belongs to
     * @param key        the slot key of the slot value we are looking for
     * @return slot value as 32-length left padded Bytes
     */
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return contractStateRepository.findStorage(contractId.getId(), key);
        }
        final var cachedValue = contractStateCache.get(generateCacheKey(contractId, key));
        updateCachedSlotKeys(contractId, key);

        if (cachedValue != null) {
            return unwrapCacheValue(cachedValue);
        }

        return findStorageBatch(contractId, key);
    }

    public Optional<byte[]> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp);
    }

    private Optional<byte[]> unwrapCacheValue(ValueWrapper cachedValue) {
        Object value = cachedValue.get();
        if (value instanceof byte[] bytes && bytes.length > 0) {
            return Optional.of(bytes);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Adds slotKey to contractSlotsCache if not present. In case the max size of cache slot keys
     * is reached the oldest element from the cache is removed to free space for the new one.
     * @param contractId id of the contract that the slot key belongs to
     * @param slotKey that will be added to the contractSlotsCache if not already in there
     */
    private void updateCachedSlotKeys(final EntityId contractId, final byte[] slotKey) {
        final var encodedSlotKey = encodeSlotKey(slotKey);
        var cachedSlotKeys = contractSlotsCache.get(contractId, Cache.class);

        if (cachedSlotKeys != null) {
            if (cachedSlotKeys.get(encodedSlotKey) == null) {
                cachedSlotKeys.put(encodedSlotKey, false);
            }
        } else {
            cachedSlotKeys = cacheManager.getCache("cache-" + new Random().nextInt()); // cache created lazily
            if (cachedSlotKeys != null) {
                cachedSlotKeys.put(encodedSlotKey, false);
            }
            contractSlotsCache.put(contractId, cachedSlotKeys);
        }
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result.
     * The goal of the query is to preload previously requested data to avoid additional queries against the db.
     * @param contractId id of the contract that the slotKey-value pairs are queried for.
     * @return slotKey-value pairs for contractId
     */
    private Optional<byte[]> findStorageBatch(final EntityId contractId, final byte[] key) {
        final var cachedSlotKeys = contractSlotsCache.get(contractId, Cache.class);
        if (cachedSlotKeys == null) {
            return Optional.empty();
        }

        final var cachedSlotsCaffeine =
                ((CaffeineCache) cachedSlotKeys).getNativeCache().asMap().keySet();

        final List<byte[]> cachedSlots = new ArrayList<>(cachedSlotsCaffeine.size());
        for (Object slot : cachedSlotsCaffeine) {
            byte[] bytes = decodeSlotKey(String.valueOf(slot));
            cachedSlots.add(bytes);
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
     * Caches the slot values returned as part of the findStorageBatch query.
     * @param contractId id of the contract that the slot values will be cached for
     * @param slotKeyValuePairs the slot key value pairs returned from the findStorageBatch query
     */
    private void cacheSlotValues(final EntityId contractId, final List<ContractSlotValue> slotKeyValuePairs) {
        for (final ContractSlotValue slotKeyValuePair : slotKeyValuePairs) {
            final byte[] slotKey = slotKeyValuePair.getSlot();
            final byte[] slotValue = slotKeyValuePair.getValue();
            if (slotValue != null) {
                contractStateCache.put(generateCacheKey(contractId, slotKey), slotValue);
            }
        }
    }

    /**
     * Caches the slot keys that were passed to a findStorage/findStorageBatch query, but were not present in the db.
     * @param contractId id of the contract that the slot keys were queried for
     * @param nonExistingSlots the slot keys queried for the contractId that were not present in the db
     */
    private void cacheNonExistingSlots(final EntityId contractId, final List<byte[]> nonExistingSlots) {
        for (final byte[] slot : nonExistingSlots) {
            contractStateCache.put(generateCacheKey(contractId, slot), new byte[0]);
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
    private SimpleKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId, slotKey);
    }
}
