// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.services.utils.EntityIdUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.hiero.mirror.web3.state.keyvalue.ContractStorageReadableKVState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.stereotype.Service;

@CustomLog
@Service
final class ContractStateServiceImpl implements ContractStateService {

    private static final byte[] EMPTY_VALUE = new byte[0];

    private final CacheManager cacheManagerSlotsPerContract;
    private final CacheProperties cacheProperties;
    private final Cache contractSlotsCache;
    private final Cache contractStateCache;
    private final ContractStateRepository contractStateRepository;

    ContractStateServiceImpl(
            final @Qualifier(CACHE_MANAGER_CONTRACT_SLOTS) CacheManager cacheManagerContractSlots,
            final @Qualifier(CACHE_MANAGER_CONTRACT_STATE) CacheManager cacheManagerContractState,
            final @Qualifier(CACHE_MANAGER_SLOTS_PER_CONTRACT) CacheManager cacheManagerSlotsPerContract,
            final CacheProperties cacheProperties,
            final ContractStateRepository contractStateRepository) {
        this.cacheManagerSlotsPerContract = cacheManagerSlotsPerContract;
        this.cacheProperties = cacheProperties;
        this.contractSlotsCache = cacheManagerContractSlots.getCache(CACHE_NAME);
        this.contractStateCache = cacheManagerContractState.getCache(CACHE_NAME);
        this.contractStateRepository = contractStateRepository;
    }

    /**
     * Executes findStorageBatch query if the slot value is not cached.
     *
     * @param contractId Entity ID of the contract that the slot key belongs to
     * @param key        The slot key of the slot value we are looking for
     * @return slot value as 32-length left padded Bytes
     */
    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return contractStateRepository.findStorage(contractId.getId(), key);
        }

        final var cachedValue = contractStateCache.get(generateCacheKey(contractId, key), byte[].class);

        if (cachedValue != null && cachedValue != EMPTY_VALUE) {
            return Optional.of(cachedValue);
        }

        return findStorageBatch(contractId, key);
    }

    @Override
    public Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId.getId(), slotKeyByteArray, blockTimestamp);
    }

    @Override
    public void warmStorageKeys(final EntityId contractId) {
        final var ctx = ContractCallContext.get();
        final var contractStorageKeys = ctx.getReadCacheState(ContractStorageReadableKVState.STATE_ID);
        final var slotsToSearch = new ArrayList<byte[]>();

        if (ctx.isStorageDiscoveryModeFinished()) {
            for (final var contractStorageKeyEntry : contractStorageKeys.entrySet()) {
                if (contractStorageKeyEntry.getKey() instanceof SlotKey slotKey
                        && slotKey.contractID() != null
                        && EntityIdUtils.toEntityId(slotKey.contractID()).equals(contractId)) {
                    slotsToSearch.add(slotKey.key().toByteArray());
                    contractSlotsCache.putIfAbsent(slotKey.key().toByteArray(), EMPTY_VALUE);
                }
            }
        }

        final var contractSlotValues = contractStateRepository.findStorageBatch(contractId.getId(), slotsToSearch);

        for (final var contractSlotValue : contractSlotValues) {
            final var slotKey = contractSlotValue.getSlot();
            final var slotValue = contractSlotValue.getValue();
            contractStateCache.put(generateCacheKey(contractId, slotKey), slotValue);
        }
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result. The goal of the
     * query is to preload previously requested data to avoid additional queries against the db.
     *
     * @param contractId id of the contract that the slotKey-value pairs are queried for.
     * @return slotKey-value pairs for contractId
     */
    private Optional<byte[]> findStorageBatch(final EntityId contractId, final byte[] key) {
        final var contractSlotsCacheForContract = ((CaffeineCache) this.contractSlotsCache.get(
                contractId, () -> cacheManagerSlotsPerContract.getCache(contractId.toString())));
        final var wrappedKey = ByteBuffer.wrap(key);
        // Track the requested slot key so it is included in the batch query.
        contractSlotsCacheForContract.putIfAbsent(wrappedKey, EMPTY_VALUE);

        final var cachedSlotKeys =
                contractSlotsCacheForContract.getNativeCache().asMap().keySet();

        final var slotsToSearch = new ArrayList<byte[]>();
        for (final var slot : cachedSlotKeys) {
            slotsToSearch.add(((ByteBuffer) slot).array());
        }

        final var contractSlotValues = contractStateRepository.findStorageBatch(contractId.getId(), slotsToSearch);
        byte[] foundValue = null;

        for (final var contractSlotValue : contractSlotValues) {
            final var slotKey = contractSlotValue.getSlot();
            final var slotValue = contractSlotValue.getValue();
            contractStateCache.put(generateCacheKey(contractId, slotKey), slotValue);

            if (Arrays.equals(slotKey, key)) {
                foundValue = slotValue;
            }

            // Remove found slot keys from the slots cache, since their values are now cached in the contract state
            // cache and the next batch call does not need to query for them again.
            contractSlotsCacheForContract.evictIfPresent(ByteBuffer.wrap(slotKey));
        }

        return Optional.ofNullable(foundValue);
    }

    // Generates a cache key emulating the default caching behavior in Spring
    private SimpleKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId, slotKey);
    }
}
