// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOT_TRACKING;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOT_TRACKING_MAX_SLOT_CACHED;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_CONTRACT_SLOT_TRACKING;

import jakarta.inject.Named;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
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
        Object cacheKey = SimpleKeyGenerator.generateKey(contractId, key);
        if (findStorageCache.get(cacheKey) == null) {
            Set<String> cachedSlotKeys = queriedSlotsCache.get(contractId, LinkedHashSet::new);
            String queriedSlotKey = Base64.getEncoder().encodeToString(key);
            if (cachedSlotKeys != null) {
                if (!cachedSlotKeys.contains(queriedSlotKey)) {
                    cachedSlotKeys.add(queriedSlotKey);
                    if (cachedSlotKeys.size() > CACHE_MANAGER_CONTRACT_SLOT_TRACKING_MAX_SLOT_CACHED) {
                        Iterator<String> it = cachedSlotKeys.iterator();
                        if (it.hasNext()) {
                            it.next();
                            it.remove();
                        }
                    }
                    queriedSlotsCache.put(contractId, cachedSlotKeys);
                }

                if (cachedSlotKeys.size() > 1) {
                    List<byte[]> decodedCachedSlotKeys = cachedSlotKeys.stream()
                            .map(Base64.getDecoder()::decode)
                            .toList();

                    List<ContractSlotValue> slotKeyValuePairs =
                            contractStateRepository.findStorageBatch(contractId, decodedCachedSlotKeys);
                    for (ContractSlotValue slotKeyValuePair : slotKeyValuePairs) {
                        byte[] slotKey = slotKeyValuePair.slot();
                        byte[] slotValue = slotKeyValuePair.value();

                        if (slotValue != null) {
                            findStorageCache.put(SimpleKeyGenerator.generateKey(contractId, slotKey), slotValue);
                        }
                    }
                }
            }
        }

        return contractStateRepository.findStorage(contractId, key);
    }

    public Optional<byte[]> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp);
    }
}
