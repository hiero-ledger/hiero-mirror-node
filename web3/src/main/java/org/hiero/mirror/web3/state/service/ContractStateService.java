// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.state.Utils.convertToLeftPaddedBytes;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.hiero.mirror.web3.state.Utils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
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
     * @return slot value as 32-length left padded Bytes
     */
    public Optional<Bytes> findStorage(final EntityId contractId, final byte[] key) {
        final var cachedValue = contractStateCache.get(generateCacheKey(contractId, key));
        updateCachedSlotKeys(contractId, key);

        if (cachedValue == null) {
            final var slotKeyValuePairs = findStorageBatch(contractId);

            return slotKeyValuePairs.stream()
                    .filter(slotKeyValuePair -> Arrays.equals(slotKeyValuePair.getSlot(), key))
                    .map(ContractSlotValue::getValue)
                    .map(Utils::convertToLeftPaddedBytes)
                    .findFirst();
        } else {
            Object cachedVal = cachedValue.get();
            return Optional.of(convertToLeftPaddedBytes((byte[]) cachedVal));
        }
    }

    public Optional<Bytes> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository
                .findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp)
                .map(Utils::convertToLeftPaddedBytes);
    }

    /**
     * Adds slotKey to queriedSlotsCache if not present. In case the max size of cache slot keys
     * is reached the oldest element from the cache is removed to free space for the new one.
     * @param contractId id of the contract that the slot key belongs to
     * @param slotKey that will be added to the queriedSlotsCache if not already in there
     */
    private synchronized void updateCachedSlotKeys(EntityId contractId, byte[] slotKey) {
        final var encodeSlotKey = encodeSlotKey(slotKey);
        Set<String> cachedSlotKeys = contractSlotsCache.get(contractId, LinkedHashSet::new);

        if (cachedSlotKeys != null) {
            if (!cachedSlotKeys.contains(encodeSlotKey)) {
                if (cachedSlotKeys.size() >= cacheProperties.getMaxSlotsPerContract()) {
                    Iterator<String> it = cachedSlotKeys.iterator();
                    it.next();
                    it.remove();
                }
                cachedSlotKeys.add(encodeSlotKey);
                contractSlotsCache.put(contractId, cachedSlotKeys);
            }
        } else {
            cachedSlotKeys = new LinkedHashSet<>();
            cachedSlotKeys.add(encodeSlotKey);
            contractSlotsCache.put(contractId, cachedSlotKeys);
        }
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result.
     * The goal of the query is to preload previously requested data to avoid additional queries against the db.
     * @param contractId id of the contract that the slotKey-value pairs are queried for.
     * @return slotKey-value pairs for contractId
     */
    private List<ContractSlotValue> findStorageBatch(final EntityId contractId) {
        final Set<String> cachedSlotKeys = contractSlotsCache.get(contractId, LinkedHashSet::new);
        if (cachedSlotKeys == null || cachedSlotKeys.isEmpty()) {
            return List.of();
        }
        final var cachedSlots = cachedSlotKeys.stream().map(this::decodeSlotKey).toList();

        final var existingSlotKeyValuePairs = contractStateRepository.findStorageBatch(contractId.getId(), cachedSlots);
        final var existingSlots = existingSlotKeyValuePairs.stream()
                .map(pair -> Bytes.wrap(pair.getSlot()))
                .collect(Collectors.toSet());
        cacheSlotValues(contractId, existingSlotKeyValuePairs);

        final var nonExistingSlots = cachedSlots.stream()
                .filter(key -> !existingSlots.contains(Bytes.wrap(key)))
                .toList();
        cacheNonExistingSlots(contractId, nonExistingSlots);

        return existingSlotKeyValuePairs;
    }

    /**
     * Caches the slot values returned as part of the findStorageBatch query.
     * @param contractId id of the contract that the slot values will be cached for
     * @param slotKeyValuePairs the slot key value pairs returned from the findStorageBatch query
     */
    private synchronized void cacheSlotValues(
            final EntityId contractId, final List<ContractSlotValue> slotKeyValuePairs) {
        for (ContractSlotValue slotKeyValuePair : slotKeyValuePairs) {
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
    private synchronized void cacheNonExistingSlots(final EntityId contractId, final List<byte[]> nonExistingSlots) {
        for (byte[] slot : nonExistingSlots) {
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
