// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_SLOTS;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_CONTRACT_STATE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SLOTS_PER_CONTRACT;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.common.ContractCallContext;
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
    private final ConcurrentHashMap<SimpleKey, CompletableFuture<Optional<byte[]>>> inFlight =
            new ConcurrentHashMap<>();

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

    @Override
    public Optional<byte[]> findStorage(final EntityId contractId, final byte[] key) {
        if (!cacheProperties.isEnableBatchContractSlotCaching()) {
            return contractStateRepository.findStorage(contractId.getId(), key);
        }

        final var cacheKey = generateCacheKey(contractId, key);
        final var cachedValue = contractStateCache.get(cacheKey, byte[].class);
        if (cachedValue != null) {
            return toOptional(cachedValue);
        }

        final var created = new CompletableFuture<Optional<byte[]>>();
        final var existing = inFlight.putIfAbsent(cacheKey, created);
        if (existing != null) {
            // Never await a future this call owns — that would self-deadlock on re-entrant lookups.
            if (isOwnedInFlightSlot(cacheKey)) {
                log.warn(
                        "Reentrant contract storage lookup for contract {} slot; falling back to direct query",
                        contractId);
                return loadAndCache(contractId, key, cacheKey);
            }
            return await(existing, contractId, key, cacheKey);
        }

        withContext(ctx -> ctx.markOwnedInFlightSlot(cacheKey));
        try {
            return findStorageBatch(contractId, key, cacheKey, created);
        } catch (final Exception e) {
            created.completeExceptionally(e);
            rethrow(e);
        } finally {
            withContext(ctx -> ctx.unmarkOwnedInFlightSlot(cacheKey));
            inFlight.remove(cacheKey, created);
        }

        return Optional.empty();
    }

    @Override
    public Optional<byte[]> findStorageByBlockTimestamp(
            final EntityId entityId, final byte[] slotKeyByteArray, final long blockTimestamp) {
        return contractStateRepository.findStorageByBlockTimestamp(entityId.getId(), slotKeyByteArray, blockTimestamp);
    }

    private Optional<byte[]> findStorageBatch(
            final EntityId contractId,
            final byte[] key,
            final SimpleKey primaryKey,
            final CompletableFuture<Optional<byte[]>> primaryFuture) {
        final var contractSlotsCache = ((CaffeineCache) this.contractSlotsCache.get(
                contractId, () -> cacheManagerSlotsPerContract.getCache(contractId.toString())));
        final var wrappedKey = ByteBuffer.wrap(key);
        contractSlotsCache.putIfAbsent(wrappedKey, EMPTY_VALUE);

        final var cachedSlotKeys = contractSlotsCache.getNativeCache().asMap().keySet();
        final var maxSlotKeysPerBatch = cacheProperties.getMaxSlotKeysPerBatch();
        final var cachedSlots = new ArrayList<byte[]>(Math.min(cachedSlotKeys.size(), maxSlotKeysPerBatch));
        final var ownedFlights = new HashMap<SimpleKey, CompletableFuture<Optional<byte[]>>>();
        ownedFlights.put(primaryKey, primaryFuture);

        boolean primarySeen = false;
        for (final var slotKey : cachedSlotKeys) {
            if (cachedSlots.size() >= maxSlotKeysPerBatch) {
                break;
            }

            final var slotKeyBytes = ((ByteBuffer) slotKey).array();
            final var slotValueCacheKey = generateCacheKey(contractId, slotKeyBytes);
            final boolean isPrimary = wrappedKey.equals(slotKey);
            if (isPrimary) {
                primarySeen = true;
                cachedSlots.add(slotKeyBytes);
            }

            if (contractStateCache.get(slotValueCacheKey) != null) {
                continue;
            }

            final var nestedFlight = new CompletableFuture<Optional<byte[]>>();
            if (inFlight.putIfAbsent(slotValueCacheKey, nestedFlight) == null) {
                withContext(ctx -> ctx.markOwnedInFlightSlot(slotValueCacheKey));
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

            if (!primarySeen) {
                final var result = contractStateRepository.findStorage(contractId.getId(), key);
                contractStateCache.put(primaryKey, result.orElse(EMPTY_VALUE));
            }

            completeOwnedFlights(ownedFlights);
            return toOptional(contractStateCache.get(primaryKey, byte[].class));
        } catch (Exception e) {
            failOwnedFlights(ownedFlights, e);
            rethrow(e);
        } finally {
            releaseNestedFlights(ownedFlights, primaryKey);
        }

        return Optional.empty();
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

    private void releaseNestedFlights(
            final Map<SimpleKey, CompletableFuture<Optional<byte[]>>> ownedFlights, final SimpleKey primaryKey) {
        for (final var entry : ownedFlights.entrySet()) {
            if (!primaryKey.equals(entry.getKey())) {
                withContext(ctx -> ctx.unmarkOwnedInFlightSlot(entry.getKey()));
                inFlight.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private Optional<byte[]> await(
            final CompletableFuture<Optional<byte[]>> future,
            final EntityId contractId,
            final byte[] key,
            final SimpleKey cacheKey) {
        try {
            return future.get(cacheProperties.getInFlightWaitTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException _) {
            log.warn(
                    "Timed out after {} waiting for in-flight contract storage load for contract {}; falling back to direct query",
                    cacheProperties.getInFlightWaitTimeout(),
                    contractId);
            inFlight.remove(cacheKey, future);
            final var cachedValue = contractStateCache.get(cacheKey, byte[].class);
            if (cachedValue != null) {
                return toOptional(cachedValue);
            }
            return loadAndCache(contractId, key, cacheKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for contract storage load", e);
        } catch (ExecutionException e) {
            final var cause = e.getCause() != null ? e.getCause() : e;
            rethrow(cause);
        }

        return Optional.empty();
    }

    private Optional<byte[]> loadAndCache(final EntityId contractId, final byte[] key, final SimpleKey cacheKey) {
        final var result = contractStateRepository.findStorage(contractId.getId(), key);
        contractStateCache.put(cacheKey, result.orElse(EMPTY_VALUE));
        return result;
    }

    private static void withContext(final Consumer<ContractCallContext> action) {
        if (ContractCallContext.isInitialized()) {
            action.accept(ContractCallContext.get());
        }
    }

    private static boolean isOwnedInFlightSlot(final SimpleKey cacheKey) {
        return ContractCallContext.isInitialized() && ContractCallContext.get().isOwnedInFlightSlot(cacheKey);
    }

    private static void rethrow(final Throwable t) {
        if (t instanceof Error error) {
            throw error;
        }
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException("Failed to load contract storage", t);
    }

    private static Optional<byte[]> toOptional(final byte[] cachedValue) {
        if (cachedValue == null || cachedValue == EMPTY_VALUE) {
            return Optional.empty();
        }
        return Optional.of(cachedValue);
    }

    private static SimpleKey generateCacheKey(final EntityId contractId, final byte[] slotKey) {
        return new SimpleKey(contractId.getId(), slotKey);
    }
}
