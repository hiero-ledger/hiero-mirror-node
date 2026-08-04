// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.repository.ContractStateRepository;
import org.hiero.mirror.web3.repository.properties.CacheProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.stereotype.Service;

/**
 * Loads contract storage with batch prefetch, negative caching ({@code EMPTY_VALUE}), and per-slot single-flight
 * coalescing so concurrent misses share one DB query.
 */
@CustomLog
@Service
final class ContractStateServiceImpl implements ContractStateService {

    private static final byte[] EMPTY_VALUE = new byte[0];

    private final CacheManager cacheManagerSlotsPerContract;
    private final CacheProperties cacheProperties;
    private final Cache contractSlotsCache;
    private final Cache contractStateCache;
    private final ContractStateRepository contractStateRepository;
    /**
     * Bounded single-flight cache. Presence of a key means a load is already in progress for that contract state slot;
     * waiters coalesce on the stored future, and batch assembly skips keys already in flight.
     */
    private final LoadingCache<SimpleKey, CompletableFuture<Optional<byte[]>>> inFlightCache;

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
        this.inFlightCache =
                Caffeine.from(cacheProperties.getContractStateInFlight()).build(key -> new CompletableFuture<>());
    }

    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        final var cacheKey = generateCacheKey(contractId, key);

        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return loadAndCache(contractId, key, cacheKey);
        }

        final var cachedValue = contractStateCache.get(cacheKey, byte[].class);
        if (cachedValue != null) {
            return toOptional(cachedValue);
        }

        final var created = new CompletableFuture<Optional<byte[]>>();
        final var existing = claimInFlight(cacheKey, created);
        if (existing != null) {
            return await(existing, contractId, key, cacheKey);
        }

        try {
            return findStorageBatch(contractId, key, cacheKey, created);
        } catch (final Exception e) {
            created.completeExceptionally(e);
            throw e;
        } finally {
            releaseInFlight(cacheKey, created);
        }
    }

    @Override
    public Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId.getId(), slotKeyByteArray, blockTimestamp);
    }

    private Optional<byte[]> findStorageBatch(
            final EntityId contractId,
            final byte[] key,
            final SimpleKey keyToSearch,
            final CompletableFuture<Optional<byte[]>> primaryFuture) {
        final var contractSlotsCache = ((CaffeineCache) this.contractSlotsCache.get(
                contractId, () -> cacheManagerSlotsPerContract.getCache(contractId.toString())));
        final var wrappedKey = ByteBuffer.wrap(key);
        contractSlotsCache.putIfAbsent(wrappedKey, EMPTY_VALUE);

        final var cachedSlotKeys = contractSlotsCache.getNativeCache().asMap().keySet();
        final var maxSlotKeysPerBatch = cacheProperties.getMaxSlotKeysPerBatch();
        final var cachedSlots = new ArrayList<byte[]>(Math.min(cachedSlotKeys.size(), maxSlotKeysPerBatch));
        final var ownedFlights = new HashMap<SimpleKey, CompletableFuture<Optional<byte[]>>>();
        ownedFlights.put(keyToSearch, primaryFuture);

        boolean keySeen = false;
        for (final var slotKey : cachedSlotKeys) {
            if (cachedSlots.size() >= maxSlotKeysPerBatch) {
                break;
            }

            final var slotKeyBytes = toByteArray((ByteBuffer) slotKey);
            final var slotValueCacheKey = generateCacheKey(contractId, slotKeyBytes);
            final boolean keyFound = wrappedKey.equals(slotKey);
            if (keyFound) {
                keySeen = true;
                cachedSlots.add(slotKeyBytes);
            }

            if (contractStateCache.get(slotValueCacheKey) != null) {
                continue;
            }

            final var nestedFlight = new CompletableFuture<Optional<byte[]>>();
            if (claimInFlight(slotValueCacheKey, nestedFlight) == null) {
                ownedFlights.put(slotValueCacheKey, nestedFlight);
                cachedSlots.add(slotKeyBytes);
            }
        }

        try {
            final var contractSlotValues = contractStateRepository.findStorageBatch(contractId.getId(), cachedSlots);
            final var foundSlots = HashSet.newHashSet(contractSlotValues.size());

            for (final var contractSlotValue : contractSlotValues) {
                final var slotKey = contractSlotValue.getSlot();
                foundSlots.add(ByteBuffer.wrap(slotKey));
                contractStateCache.put(generateCacheKey(contractId, slotKey), contractSlotValue.getValue());
            }

            for (final var slotKeyBytes : cachedSlots) {
                if (!foundSlots.contains(ByteBuffer.wrap(slotKeyBytes))) {
                    contractStateCache.put(generateCacheKey(contractId, slotKeyBytes), EMPTY_VALUE);
                }
            }

            if (!keySeen) {
                final var result = contractStateRepository.findStorage(contractId.getId(), key);
                contractStateCache.put(keyToSearch, result.orElse(EMPTY_VALUE));
            }

            completeOwnedFlights(ownedFlights);
            return toOptional(contractStateCache.get(keyToSearch, byte[].class));
        } catch (final Exception e) {
            failOwnedFlights(ownedFlights, e);
            throw e;
        } finally {
            releaseOwnedFlights(ownedFlights, keyToSearch);
        }
    }

    private void completeOwnedFlights(final Map<SimpleKey, CompletableFuture<Optional<byte[]>>> ownedFlights) {
        for (final var entry : ownedFlights.entrySet()) {
            entry.getValue().complete(toOptional(contractStateCache.get(entry.getKey(), byte[].class)));
        }
    }

    private void failOwnedFlights(
            final Map<SimpleKey, CompletableFuture<Optional<byte[]>>> ownedFlights, final Throwable t) {
        for (final var future : ownedFlights.values()) {
            future.completeExceptionally(t);
        }
    }

    private void releaseOwnedFlights(
            final Map<SimpleKey, CompletableFuture<Optional<byte[]>>> ownedFlights, final SimpleKey primaryKey) {
        for (final var entry : ownedFlights.entrySet()) {
            if (!primaryKey.equals(entry.getKey())) {
                releaseInFlight(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Awaits an in-flight load bounded by {@code inFlightWaitTimeout}. On timeout, checks the value cache and falls
     * back to a direct DB query if needed.
     */
    private Optional<byte[]> await(
            final CompletableFuture<Optional<byte[]>> future,
            final EntityId contractId,
            final byte[] key,
            final SimpleKey cacheKey) {
        final var timeout = cacheProperties.getInFlightWaitTimeout();
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException _) {
            log.warn(
                    "Timed out after {} waiting for in-flight contract storage load for contract {}; falling back to direct query",
                    timeout,
                    contractId);
            releaseInFlight(cacheKey, future);
            final var cachedValue = contractStateCache.get(cacheKey, byte[].class);
            if (cachedValue != null) {
                return toOptional(cachedValue);
            }
            return loadAndCache(contractId, key, cacheKey);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for contract storage load", e);
        } catch (final ExecutionException e) {
            throw asUnchecked(e.getCause() != null ? e.getCause() : e);
        }
    }

    private Optional<byte[]> loadAndCache(final EntityId contractId, final byte[] key, final SimpleKey cacheKey) {
        final var result = contractStateRepository.findStorage(contractId.getId(), key);
        contractStateCache.put(cacheKey, result.orElse(EMPTY_VALUE));
        return result;
    }

    /**
     * Atomically claims ownership of an in-flight load for {@code key}.
     *
     * @return {@code null} if {@code created} was stored (this caller owns the load); otherwise the existing in-flight
     * future that waiters should coalesce on. Keys already in flight are not added to a batch DB query.
     */
    private CompletableFuture<Optional<byte[]>> claimInFlight(
            final SimpleKey key, final CompletableFuture<Optional<byte[]>> created) {
        final var future = inFlightCache.get(key, k -> created);
        // Same instance means this caller inserted it; a different instance is an existing flight to join.
        return future == created ? null : future;
    }

    /**
     * Atomically removes the in-flight entry only when it still refers to {@code expected}, so a newer flight for the
     * same key is not cleared by a timed-out or finished owner.
     */
    private void releaseInFlight(final SimpleKey key, final CompletableFuture<Optional<byte[]>> expected) {
        inFlightCache.asMap().remove(key, expected);
    }

    private static RuntimeException asUnchecked(final Throwable t) {
        if (t instanceof Error error) {
            throw error;
        }
        if (t instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Failed to load contract storage", t);
    }

    private static Optional<byte[]> toOptional(final byte[] cachedValue) {
        if (cachedValue == null || cachedValue == EMPTY_VALUE) {
            return Optional.empty();
        }
        return Optional.of(cachedValue);
    }

    /**
     * Copies the buffer's readable bytes so sliced/duplicated buffers do not expose a larger backing array.
     */
    private static byte[] toByteArray(final ByteBuffer buffer) {
        final var view = buffer.asReadOnlyBuffer();
        final var bytes = new byte[view.remaining()];
        view.get(bytes);
        return bytes;
    }

    private static SimpleKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId.getId(), slotKey);
    }
}
