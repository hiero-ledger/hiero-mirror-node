// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;

@SuppressWarnings({"unchecked", "deprecation"})
public final class CaffeineWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    // Sentinel stored in the cache to indicate a key was explicitly deleted
    static final Object TOMBSTONE = new Object();

    private final ReadableKVState<K, V> readableBackingStore;
    private final Cache<K, Object> sharedStore;

    public CaffeineWritableKVState(
            @NonNull final String serviceName,
            final int stateId,
            @NonNull final ReadableKVState<K, V> readableBackingStore,
            @NonNull final Cache<K, Object> sharedStore) {
        super(serviceName, stateId);
        this.readableBackingStore = readableBackingStore;
        this.sharedStore = sharedStore;
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        final var cached = sharedStore.getIfPresent(key);
        if (cached != null) {
            return cached == TOMBSTONE ? null : (V) cached;
        }
        return readableBackingStore.get(key);
    }

    // Called only from commit(); writes directly to the shared store, bypassing the per-request cache.
    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        sharedStore.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        sharedStore.put(key, TOMBSTONE);
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
}
