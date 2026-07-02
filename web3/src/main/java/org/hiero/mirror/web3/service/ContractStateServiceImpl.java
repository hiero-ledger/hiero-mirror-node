// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.services.utils.EntityIdUtils;
import java.math.BigInteger;
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

    private static final int ADJACENT_SLOTS_TO_CACHE = 5;
    private static final int INITIAL_SLOT_INDEXES_TO_CACHE = 100;
    private static final BigInteger UINT256_MAX = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
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

        final var contractSlotValues =
                contractStateRepository.findStorageBatch(contractId.getId(), slotsToSearch.toArray(byte[][]::new));

        for (final var contractSlotValue : contractSlotValues) {
            final var slotKey = contractSlotValue.getSlot();
            final var slotValue = contractSlotValue.getValue();
            contractStateCache.put(generateCacheKey(contractId, slotKey), slotValue);
        }
    }

    /**
     * Executes a batch query, returning slotKey-value pairs for contractId, then caches the result. The goal of the
     * query is to preload previously requested data to avoid additional queries against the db. On the first lookup
     * for a contract, slot keys for indexes {@code 0} through {@code INITIAL_SLOT_INDEXES_TO_CACHE - 1} are loaded
     * with a primary-key range query. Each lookup also registers the requested slot key and the next
     * {@value #ADJACENT_SLOTS_TO_CACHE} consecutive slot keys for prefetching.
     *
     * @param contractId id of the contract that the slotKey-value pairs are queried for.
     * @return slotKey-value pairs for contractId
     */
    private Optional<byte[]> findStorageBatch(final EntityId contractId, final byte[] key) {
        final var contractSlotsCacheForContract = ((CaffeineCache) this.contractSlotsCache.get(
                contractId, () -> cacheManagerSlotsPerContract.getCache(contractId.toString())));
        final var wrappedKey = ByteBuffer.wrap(key);
        // Cached slot keys for contract, whose slot values are not present in the contractStateCache
        final var initialPrefetchDone =
                contractSlotsCacheForContract.getNativeCache().asMap().isEmpty();
        if (initialPrefetchDone) {
            cacheInitialSlotKeys(contractId, contractSlotsCache);
        }
        cacheSlotKeyAndAdjacentSlots(contractSlotsCache, key);

        final var cachedSlotKeys =
                contractSlotsCacheForContract.getNativeCache().asMap().keySet();
        boolean isKeyEvictedFromCache = true;
        for (var slot : cachedSlotKeys) {
            final var slotBytes = ((ByteBuffer) slot).array();
            if (initialPrefetchDone && isWithinInitialSlotIndexes(slotBytes)) {
                if (wrappedKey.equals(slot)) {
                    isKeyEvictedFromCache = false;
                }
                continue;
            }
            cachedSlotKeys.add(slotBytes);
            if (wrappedKey.equals(slot)) {
                isKeyEvictedFromCache = false;
            }
        }

        byte[] cachedValue = null;
        if (initialPrefetchDone && isWithinInitialSlotIndexes(key)) {
            cachedValue = contractStateCache.get(generateCacheKey(contractId, key), byte[].class);
        }

        if (!cachedSlotKeys.isEmpty()) {
            final var contractSlotValues =
                    contractStateRepository.findStorageBatch(contractId.getId(), cachedSlotKeys.toArray(byte[][]::new));

            for (final var contractSlotValue : contractSlotValues) {
                final byte[] slotKey = contractSlotValue.getSlot();
                final byte[] slotValue = contractSlotValue.getValue();
                contractStateCache.put(generateCacheKey(contractId, slotKey), slotValue);

                contractSlotsCacheForContract.evictIfPresent(ByteBuffer.wrap(slotKey));

                if (Arrays.equals(slotKey, key)) {
                    cachedValue = slotValue;
                }
            }
        }

        // If the cache key was evicted and hasn't been requested since, the cached value will be null.
        // In that case, fall back to the original query.
        if (isKeyEvictedFromCache) {
            return contractStateRepository.findStorage(contractId.getId(), key);
        }

        return Optional.ofNullable(cachedValue);
    }

    private void cacheInitialSlotKeys(final EntityId contractId, final Cache contractSlotsCache) {
        final var maxSlotIndex = INITIAL_SLOT_INDEXES_TO_CACHE - 1;
        final var initialSlots = contractStateRepository.findInitialStorageSlots(contractId.getId(), maxSlotIndex);

        for (final var contractSlotValue : initialSlots) {
            final byte[] slotKey = contractSlotValue.getSlot();
            contractStateCache.put(generateCacheKey(contractId, slotKey), contractSlotValue.getValue());
            contractSlotsCache.putIfAbsent(ByteBuffer.wrap(slotKey), EMPTY_VALUE);
        }

        for (int i = 0; i < INITIAL_SLOT_INDEXES_TO_CACHE; i++) {
            contractSlotsCache.putIfAbsent(ByteBuffer.wrap(toPaddedSlotKey(BigInteger.valueOf(i))), EMPTY_VALUE);
        }
    }

    private boolean isWithinInitialSlotIndexes(final byte[] key) {
        return new BigInteger(1, key).compareTo(BigInteger.valueOf(INITIAL_SLOT_INDEXES_TO_CACHE)) < 0;
    }

    private void cacheSlotKeyAndAdjacentSlots(final Cache contractSlotsCache, final byte[] key) {
        var slotKey = key;
        contractSlotsCache.putIfAbsent(ByteBuffer.wrap(slotKey), EMPTY_VALUE);
        for (int i = 0; i < ADJACENT_SLOTS_TO_CACHE; i++) {
            final var nextSlotKey = incrementSlotKey(slotKey);
            if (nextSlotKey == null) {
                break;
            }
            contractSlotsCache.putIfAbsent(ByteBuffer.wrap(nextSlotKey), EMPTY_VALUE);
            slotKey = nextSlotKey;
        }
    }

    private byte[] incrementSlotKey(final byte[] key) {
        final var value = new BigInteger(1, key);
        if (value.equals(UINT256_MAX)) {
            return null;
        }
        return toPaddedSlotKey(value.add(BigInteger.ONE));
    }

    private byte[] toPaddedSlotKey(final BigInteger value) {
        final var bytes = value.toByteArray();
        final var result = new byte[32];
        final var offset = Math.max(0, bytes.length - 32);
        final var length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, offset, result, 32 - length, length);
        return result;
    }

    // Generates a cache key emulating the default caching behavior in Spring
    private SimpleKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId, slotKey);
    }
}
