// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOT_TRACKING;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT_SLOT_TRACKING;

import jakarta.inject.Named;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

@CustomLog
@Named
public class ContractStateService {

    private final ContractStateRepository contractStateRepository;
    private final Cache findStorageCache;
    private final Cache queriedSlotsCache;
    private final CacheProperties cacheProperties;

    protected ContractStateService(
            final ContractStateRepository contractStateRepository,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager contractStateCacheManager,
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOT_TRACKING) CacheManager contractSlotTrackingCacheManager,
            final CacheProperties cacheProperties) {
        this.contractStateRepository = contractStateRepository;
        this.findStorageCache =
                requireNonNull(contractStateCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.queriedSlotsCache = requireNonNull(
                contractSlotTrackingCacheManager.getCache(CACHE_NAME_CONTRACT_SLOT_TRACKING),
                "Cache not found: " + CACHE_NAME_CONTRACT_SLOT_TRACKING);
        this.cacheProperties = cacheProperties;
    }

    /**
     * Executes findStorageBatch query if the slot value is not cached.
     * @param contractId id of the contract that the slot key belongs to
     * @param key the slot key of the slot value we are looking for
     * @return slot value
     */
    public Optional<byte[]> findStorage(final Long contractId, final byte[] key) {
        final var cachedValue = findStorageCache.get(generateCacheKey(contractId, key));
        updateCachedSlotKeys(contractId, key);

        if (cachedValue == null) {
            final Set<String> cachedSlotKeys = queriedSlotsCache.get(contractId, LinkedHashSet::new);
            final var slotKeyValuePairs = findStorageBatch(contractId, cachedSlotKeys);

            return slotKeyValuePairs.stream()
                    .filter(slotKeyValuePair -> Arrays.equals(slotKeyValuePair.slot(), key))
                    .map(ContractSlotValue::value)
                    .filter(Objects::nonNull)
                    .findFirst();
        } else {
            Object cachedVal = cachedValue.get();

            if (cachedVal instanceof Optional<?> optional) {
                return optional.map(val -> (byte[]) val);
            } else {
                return Optional.ofNullable((byte[]) cachedVal);
            }
        }
    }

    public Optional<byte[]> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp);
    }

    /**
     * Adds slotKey to queriedSlotsCache if not present. In case the max size of cache slot keys
     * is reached the oldest element from the cache is removed to free space for the new one.
     * @param contractId id of the contract that the slot key belongs to
     * @param slotKey that will be added to the queriedSlotsCache if not already in there
     */
    private void updateCachedSlotKeys(Long contractId, byte[] slotKey) {
        final var encodeSlotKey = encodeSlotKey(slotKey);
        Set<String> cachedSlotKeys = queriedSlotsCache.get(contractId, LinkedHashSet::new);
        if (cachedSlotKeys != null) {
            if (!cachedSlotKeys.contains(encodeSlotKey)) {
                if (cachedSlotKeys.size() >= cacheProperties.getCachedSlotsMaxSize()) {
                    Iterator<String> it = cachedSlotKeys.iterator();
                    it.next();
                    it.remove();
                }
                cachedSlotKeys.add(encodeSlotKey);
                queriedSlotsCache.put(contractId, cachedSlotKeys);
            }
        } else {
            cachedSlotKeys = new LinkedHashSet<>();
            cachedSlotKeys.add(encodeSlotKey);
            queriedSlotsCache.put(contractId, cachedSlotKeys);
        }
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result.
     * The goal of the query is to preload previously requested data to avoid additional queries against the db.
     * @param contractId id of the contract that the slotKey-value pairs are queried for.
     * @param cachedSlotKeys slot keys used in the WHERE IN clause of the findStorageBatch query.
     * @return slotKey-value pairs for contractId
     */
    private List<ContractSlotValue> findStorageBatch(final Long contractId, final Set<String> cachedSlotKeys) {
        if (cachedSlotKeys != null && !cachedSlotKeys.isEmpty()) {
            final var slotKeys =
                    cachedSlotKeys.stream().map(this::decodeSlotKey).toList();

            final var existingSlotKeyValuePairs = contractStateRepository.findStorageBatch(contractId, slotKeys);
            cacheSlotValues(contractId, existingSlotKeyValuePairs);

            final var nonExistingSlotKeys = getCachedSlotKeysNotPresentInDb(slotKeys, existingSlotKeyValuePairs);
            cacheNonExistingSlots(contractId, nonExistingSlotKeys);

            return existingSlotKeyValuePairs;
        }
        log.warn("findStorageBatch called with empty collection of slot keys for contractId: {}", contractId);
        return new ArrayList<>();
    }

    /**
     * Returns the slot keys that were cached in the contractSlots cache,
     * but were not present in the db.
     * Since arrays use reference-based equality and contains() uses equals() we are
     * wrapping byte arrays to ByteBuffer before filtering
     * @param slotKeys
     * @param contractSlotValuePairs
     * @return
     */
    private List<byte[]> getCachedSlotKeysNotPresentInDb(
            List<byte[]> slotKeys, List<ContractSlotValue> contractSlotValuePairs) {
        Set<ByteBuffer> returnedSlotKeyBuffers = contractSlotValuePairs.stream()
                .map(slot -> ByteBuffer.wrap(slot.slot()))
                .collect(Collectors.toSet());

        return slotKeys.stream()
                .filter(slot -> !returnedSlotKeyBuffers.contains(ByteBuffer.wrap(slot)))
                .toList();
    }

    /**
     * Caches the slot values returned as part of the findStorageBatch query.
     * @param contractId id of the contract that the slot values will be cached for
     * @param slotKeyValuePairs the slot key value pairs returned from the findStorageBatch query
     */
    private void cacheSlotValues(final Long contractId, final List<ContractSlotValue> slotKeyValuePairs) {
        for (ContractSlotValue slotKeyValuePair : slotKeyValuePairs) {
            final byte[] slotKey = slotKeyValuePair.slot();
            final byte[] slotValue = slotKeyValuePair.value();
            if (slotValue != null) {
                findStorageCache.put(generateCacheKey(contractId, slotKey), slotValue);
            }
        }
    }

    /**
     * Caches the slot keys that were passed to a findStorage/findStorageBatch query, but were not present in the db.
     * @param contractId id of the contract that the slot keys were queried for
     * @param nonExistingSlots the slot keys queried for the contractId that were not present in the db
     */
    private void cacheNonExistingSlots(final Long contractId, final List<byte[]> nonExistingSlots) {
        for (byte[] slot : nonExistingSlots) {
            findStorageCache.put(generateCacheKey(contractId, slot), Optional.empty());
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
    private Object generateCacheKey(final Long contractId, final byte[] slotKey) {
        return SimpleKeyGenerator.generateKey(contractId, encodeSlotKey(slotKey));
    }
}
