// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHED_SLOTS_MAX_SIZE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOT_TRACKING;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT_SLOT_TRACKING;

import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.state.ContractSlotValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

@Named
public class ContractStateService {

    private final ContractStateRepository contractStateRepository;
    private final Cache findStorageCache;
    private final Cache queriedSlotsCache;

    protected ContractStateService(
            final ContractStateRepository contractStateRepository,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager contractStateCacheManager,
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOT_TRACKING) CacheManager contractSlotTrackingCacheManager) {
        this.contractStateRepository = contractStateRepository;
        this.findStorageCache =
                requireNonNull(contractStateCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.queriedSlotsCache = requireNonNull(
                contractSlotTrackingCacheManager.getCache(CACHE_NAME_CONTRACT_SLOT_TRACKING),
                "Cache not found: " + CACHE_NAME_CONTRACT_SLOT_TRACKING);
    }

    public Optional<byte[]> findStorage(final Long contractId, final byte[] key) {
        final var cachedValue = findStorageCache.get(generateCacheKey(contractId, key));
        updateCachedSlotKeys(contractId, key);

        if (cachedValue == null) {
            Set<String> cachedSlotKeys = queriedSlotsCache.get(contractId, LinkedHashSet::new);
            List<ContractSlotValue> slotKeyValuePairs = findStorageBatch(contractId, cachedSlotKeys);

            return slotKeyValuePairs.stream()
                    .filter(slotKeyValuePair -> Arrays.equals(slotKeyValuePair.slot(), key))
                    .map(ContractSlotValue::value)
                    .filter(Objects::nonNull)
                    .findFirst();
        } else {
            return Optional.ofNullable((byte[]) cachedValue.get());
        }
    }

    private void updateCachedSlotKeys(Long contractId, byte[] slotKey) {
        String encodeSlotKey = encodeSlotKey(slotKey);
        Set<String> cachedSlotKeys = queriedSlotsCache.get(contractId, LinkedHashSet::new);
        if (cachedSlotKeys != null) {
            if (!cachedSlotKeys.contains(encodeSlotKey)) {
                cachedSlotKeys.add(encodeSlotKey);
                if (cachedSlotKeys.size() > CACHED_SLOTS_MAX_SIZE) {
                    Iterator<String> it = cachedSlotKeys.iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    }
                }
                queriedSlotsCache.put(contractId, cachedSlotKeys);
            }
        } else {
            cachedSlotKeys = new LinkedHashSet<>();
            cachedSlotKeys.add(encodeSlotKey);
            queriedSlotsCache.put(contractId, cachedSlotKeys);
        }
    }

    private List<ContractSlotValue> findStorageBatch(final Long contractId, Set<String> encodedSlotKeys) {
        if (encodedSlotKeys != null && !encodedSlotKeys.isEmpty()) {
            List<byte[]> decodedSlotKeys =
                    encodedSlotKeys.stream().map(this::decodeSlotKey).toList();

            List<ContractSlotValue> slotKeyValuePairs =
                    contractStateRepository.findStorageBatch(contractId, decodedSlotKeys);
            cacheSlotValues(contractId, slotKeyValuePairs);
            return slotKeyValuePairs;
        }
        return new ArrayList<>();
    }

    private void cacheSlotValues(Long contractId, List<ContractSlotValue> slotKeyValuePairs) {
        for (ContractSlotValue slotKeyValuePair : slotKeyValuePairs) {
            byte[] slotKey = slotKeyValuePair.slot();
            byte[] slotValue = slotKeyValuePair.value();
            if (slotValue != null) {
                findStorageCache.put(generateCacheKey(contractId, slotKey), slotValue);
            }
        }
    }

    private String encodeSlotKey(byte[] slotKey) {
        return Base64.getEncoder().encodeToString(slotKey);
    }

    private byte[] decodeSlotKey(String slotKeyBase64) {
        return Base64.getDecoder().decode(slotKeyBase64);
    }

    private Object generateCacheKey(final Long contractId, final byte[] slotKey) {
        return SimpleKeyGenerator.generateKey(contractId, encodeSlotKey(slotKey));
    }

    public Optional<byte[]> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp);
    }
}
