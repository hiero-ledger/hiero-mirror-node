// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import java.util.Objects;
import java.util.Optional;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;

/**
 * A {@link WritableKVStateBase} backed by a long-lived Caffeine cache that persists committed state changes
 * across contract calls. Used when {@code hiero.mirror.web3.sharedWritableState=true}.
 *
 * <p>Read priority: per-request write cache → Caffeine shared store → DB-backed delegate.
 * Deletions are stored as {@link Optional#empty()} tombstones to prevent Caffeine null-value restrictions
 * from causing false DB fallthrough after a removal.
 */
@SuppressWarnings({"unchecked", "deprecation"})
public class CaffeineWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final ReadableKVState<K, V> delegate;

    // Optional.empty() is a tombstone indicating explicit removal
    private final Cache<K, Optional<V>> sharedStore;

    public CaffeineWritableKVState(
            @NonNull final String serviceName,
            final int stateId,
            @NonNull final ReadableKVState<K, V> delegate,
            @NonNull final Cache<K, Optional<V>> sharedStore) {
        super(serviceName, stateId);
        this.delegate = Objects.requireNonNull(delegate);
        this.sharedStore = Objects.requireNonNull(sharedStore);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var cached = sharedStore.getIfPresent(key);
        if (cached != null) {
            return cached.orElse(null); // Optional.empty() = tombstone → null (no DB fallthrough)
        }
        return delegate.get(key);
    }

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
        final var writeCache = ContractCallContext.get().getWriteCacheState(getStateId());
        writeCache.forEach((rawKey, rawValue) -> {
            final K key = (K) rawKey;
            if (rawValue == null) {
                removeFromDataSource(key);
            } else {
                putIntoDataSource(key, (V) rawValue);
            }
        });
    }

    @Override
    public long sizeOfDataSource() {
        return delegate.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaffeineWritableKVState<?, ?> that)) return false;
        return Objects.equals(getStateId(), that.getStateId()) && Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateId(), delegate);
    }
}
