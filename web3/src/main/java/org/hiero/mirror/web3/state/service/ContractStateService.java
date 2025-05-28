// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.util.Optional;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.state.ContractStateKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@Named
public class ContractStateService {

    private final Cache cache;
    private final ContractStateRepository contractStateRepository;

    protected ContractStateService(
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager contractStateCacheManager,
            final ContractStateRepository contractStateRepository) {
        this.cache = requireNonNull(contractStateCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.contractStateRepository = contractStateRepository;
    }

    public Optional<byte[]> findSlotValue(final Long entityId, final byte[] slotKeyByteArray) {
        final var cacheKey = new ContractStateKey(entityId, slotKeyByteArray);

        final byte[] cachedValue = cache.get(cacheKey, byte[].class);
        if (cachedValue != null) {
            return Optional.of(cachedValue);
        }

        final var slotValues = contractStateRepository.findSlotsValuesByContractId(entityId);

        byte[] matchedValue = null;
        for (final var slotValue : slotValues) {
            final byte[] slot = slotValue.slot();
            final byte[] value = slotValue.value();
            final var generatedKey = new ContractStateKey(entityId, slot);
            cache.put(generatedKey, value);

            if (generatedKey.equals(cacheKey)) {
                matchedValue = value;
            }
        }

        if (matchedValue != null) {
            return Optional.of(matchedValue);
        }
        return contractStateRepository.findStorage(entityId, slotKeyByteArray);
    }

    public Optional<byte[]> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp);
    }
}
