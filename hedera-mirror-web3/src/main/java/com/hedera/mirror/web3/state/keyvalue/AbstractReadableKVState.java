// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import com.google.common.collect.ForwardingConcurrentMap;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    protected AbstractReadableKVState(@Nonnull String stateKey) {
        super(stateKey, getCachedForwardingMap(stateKey));
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    private static <K, V> CachedForwardingConcurrentMap<K, V> getCachedForwardingMap(@Nonnull String stateKey) {
        return new CachedForwardingConcurrentMap<>(stateKey);
    }

    @Override
    @SuppressWarnings("deprecation")
    public long size() {
        return ContractCallContext.get().getReadCacheState(getStateKey()).size();
    }

    /**
     * Each transaction is executed in its own {@link ContractCallContext}, so each transaction has its own read and
     * write cache and this map delegates to the current transaction's read cache. All the methods from the Map interface,
     * such as: get(), put(), etc. will operate on the map from the scoped read cache.
     */
    @SuppressWarnings("unchecked")
    private static class CachedForwardingConcurrentMap<K, V> extends ForwardingConcurrentMap<K, V> {

        private final String key;

        private CachedForwardingConcurrentMap(final String key) {
            this.key = key;
        }

        @Override
        protected ConcurrentHashMap<K, V> delegate() {
            return (ConcurrentHashMap<K, V>) ContractCallContext.get().getReadCacheState(key);
        }

        @Override
        public Set<K> keySet() {
            // On Spring bean initialization we don't have a ContractCallContext yet, so an empty set is passed.
            // Once we are in a running transaction, we need to use its context to handle the cache properly.
            return ContractCallContext.isInitialized()
                    ? (Set<K>) ContractCallContext.get().getReadCacheState(key).keySet()
                    : Collections.emptySet();
        }
    }
}
