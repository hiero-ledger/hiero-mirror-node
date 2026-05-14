// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import java.util.Objects;
import java.util.Optional;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;

@SuppressWarnings({"unchecked", "deprecation"})
public class CaffeineWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final ReadableKVState<K, V> readableBackingStore;

    // Optional.empty() is a tombstone indicating explicit removal
    private final Cache<K, Optional<V>> sharedStore;

    public CaffeineWritableKVState(
            @NonNull final String serviceName,
            final int stateId,
            @NonNull final ReadableKVState<K, V> readableBackingStore,
            @NonNull final Cache<K, Optional<V>> sharedStore) {
        super(serviceName, stateId);
        this.readableBackingStore = Objects.requireNonNull(readableBackingStore);
        this.sharedStore = Objects.requireNonNull(sharedStore);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var cached = sharedStore.getIfPresent(key);
        if (cached != null) {
            return cached.orElse(null); // Optional.empty() = tombstone → null (no DB fallthrough)
        }
        return readableBackingStore.get(key);
    }

    // Called only from commit(); writes directly to the shared store, bypassing the per-request cache.
    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        sharedStore.put(key, Optional.of(value));
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        sharedStore.put(key, Optional.empty());
    }

    /**
     * Flushes the per-request write cache from {@link ContractCallContext} into the shared Caffeine store,
     * making changes visible to subsequent contract calls.
     */
    @Override
    public void commit() {
        if (!ContractCallContext.isInitialized()) {
            return;
        }
        final var writeCache = ContractCallContext.get().getWriteCacheState(getStateId());
        writeCache.forEach((rawKey, rawValue) -> {
            final K key = (K) rawKey;
            if (rawValue == null) {
                removeFromDataSource(key);
            } else {
                putIntoDataSource(key, (V) rawValue);
            }
        });
        writeCache.clear();
    }

    @Override
    public long sizeOfDataSource() {
        return readableBackingStore.size();
    }

    // Bypasses readFromDataSource to avoid Caffeine skewing the backing-store presence check.
    @SuppressWarnings("deprecation")
    @Override
    public long size() {
        final long sizeOfBackingMap = readableBackingStore.size();
        int numAdditions = 0;
        int numRemovals = 0;

        final var writeCache = ContractCallContext.get().getWriteCacheState(getStateId());
        for (final var mod : writeCache.entrySet()) {
            final K key = (K) mod.getKey();
            final Object rawValue = mod.getValue();
            boolean isPresentInBackingMap = readableBackingStore.get(key) != null;
            boolean isRemovedInMod = rawValue == null;

            if (isPresentInBackingMap && isRemovedInMod) {
                numRemovals++;
            } else if (!isPresentInBackingMap && !isRemovedInMod) {
                numAdditions++;
            }
        }
        return sizeOfBackingMap + numAdditions - numRemovals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaffeineWritableKVState<?, ?> that)) return false;
        return Objects.equals(getStateId(), that.getStateId())
                && Objects.equals(readableBackingStore, that.readableBackingStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateId(), readableBackingStore);
    }
}
