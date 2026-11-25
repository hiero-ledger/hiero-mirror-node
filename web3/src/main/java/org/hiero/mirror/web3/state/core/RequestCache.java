// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.ForwardingSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A view of one of the caches the current request keeps in {@link ContractCallContext}. It resolves the cache on every
 * call and is used by one request at a time, although the read cache has to be passed as a {@link ConcurrentMap}.
 */
@SuppressWarnings("unchecked")
public final class RequestCache<K, V> extends ForwardingMap<K, V> implements ConcurrentMap<K, V> {

    private final Supplier<Map<Object, Object>> cache;

    private RequestCache(final Supplier<Map<Object, Object>> cache) {
        this.cache = cache;
    }

    public static <K, V> RequestCache<K, V> read(final int stateId) {
        return new RequestCache<>(() -> ContractCallContext.get().getReadCacheState(stateId));
    }

    public static <K, V> RequestCache<K, V> write(final int stateId) {
        return new RequestCache<>(() -> ContractCallContext.get().getWriteCacheState(stateId));
    }

    /** A view as well, because the base classes take the key set when the state is created, outside of a request. */
    @Override
    public Set<K> keySet() {
        return new ForwardingSet<>() {
            @Override
            protected Set<K> delegate() {
                return RequestCache.this.delegate().keySet();
            }
        };
    }

    @Override
    public V putIfAbsent(final K key, final V value) {
        return delegate().putIfAbsent(key, value);
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        return delegate().remove(key, value);
    }

    @Override
    public V replace(final K key, final V value) {
        return delegate().replace(key, value);
    }

    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) {
        return delegate().replace(key, oldValue, newValue);
    }

    @Override
    protected Map<K, V> delegate() {
        return (Map<K, V>) cache.get();
    }
}
