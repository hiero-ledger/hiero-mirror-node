// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.service;

import static java.util.Objects.requireNonNull;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import jakarta.inject.Named;
import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.state.ContractStateKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@Named
public class ContractStateService {

    private final Cache cache;
    private final ContractStateRepository contractStateRepository;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    protected ContractStateService(
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager contractStateCacheManager,
            final ContractStateRepository contractStateRepository,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.cache = requireNonNull(contractStateCacheManager.getCache(CACHE_NAME), "Cache not found: " + CACHE_NAME);
        this.contractStateRepository = contractStateRepository;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public Optional<byte[]> findSlotValue(final EntityId entityId, final byte[] slotKeyByteArray) {
        final var cacheKey = new ContractStateKey(entityId.getId(), slotKeyByteArray);

        final byte[] cachedValue = cache.get(cacheKey, byte[].class);
        if (cachedValue != null) {
            return Optional.of(cachedValue);
        }

        if (mirrorNodeEvmProperties.isBulkLoadStorage()) {
            final var optionalMatchedSlotValue = loadValueFromBatch(entityId, cacheKey);
            if (optionalMatchedSlotValue.isPresent()) {
                return optionalMatchedSlotValue;
            }
        }

        final var optionalStorage = contractStateRepository.findStorage(entityId.getId(), slotKeyByteArray);
        optionalStorage.ifPresent(value -> cache.put(cacheKey, value));

        return optionalStorage;
    }

    public Optional<byte[]> findStorageByBlockTimestamp(
            final Long entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId, slotKeyByteArray, blockTimestamp);
    }

    private Optional<byte[]> loadValueFromBatch(final EntityId entityId, final ContractStateKey cacheKey) {
        final var storageLoaded = ContractCallContext.get().getStorageLoaded();
        if (storageLoaded.contains(entityId)) {
            return Optional.empty();
        }
        final var slotValues = contractStateRepository.findByContractId(entityId.getId());
        storageLoaded.add(entityId);

        byte[] matchedValue = null;
        for (final var slotValue : slotValues) {
            final byte[] slot = slotValue.slot();
            final byte[] value = slotValue.value();
            final var generatedKey = new ContractStateKey(entityId.getId(), slot);
            cache.put(generatedKey, value);

            if (generatedKey.equals(cacheKey)) {
                matchedValue = value;
            }
        }

        if (matchedValue != null) {
            return Optional.of(matchedValue);
        }

        return Optional.empty();
    }
}
