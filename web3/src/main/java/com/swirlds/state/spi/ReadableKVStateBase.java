// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A base class for implementations of {@link ReadableKVState} and {@link WritableKVState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
@SuppressWarnings("unchecked")
public abstract class ReadableKVStateBase<K, V> implements ReadableKVState<K, V> {

    private static final Object marker = new Object();

    /** The state ID */
    protected final int stateId;

    /**
     * Create a new StateBase.
     *
     * @param stateId The state ID
     * @param label The state label
     */
    protected ReadableKVStateBase(final int stateId, final String label) {
        this(stateId, label, new ConcurrentHashMap<>());
    }

    /**
     * Create a new StateBase from the provided map.
     *
     * @param stateId The state ID
     * @param label The state label
     * @param readCache A map that is used to init the cache.
     */
    // This constructor is used by some consumers of the API that are outside of this repository.
    @SuppressWarnings("unused")
    protected ReadableKVStateBase(final int stateId, final String label, @NonNull ConcurrentMap<K, V> readCache) {
        this.stateId = stateId;
    }

    /** {@inheritDoc} */
    @Override
    public final int getStateId() {
        return stateId;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public V get(@NonNull K key) {
        // We need to cache the item because somebody may perform business logic basic on this
        // contains call, even if they never need the value itself!
        Objects.requireNonNull(key);
        if (!hasBeenRead(key)) {
            final var value = readFromDataSource(key);
            markRead(key, value);
        }
        final var value = getReadCache().get(key);
        return (value == marker) ? null : (V) value;
    }

    /**
     * Gets the set of keys that a client read from the {@link ReadableKVState}.
     *
     * @return The possibly empty set of keys.
     */
    @NonNull
    public final Set<K> readKeys() {
        return (Set<K>) getReadCache().keySet();
    }

    /** Clears all cached data, including the set of all read keys. */
    /*@OverrideMustCallSuper*/
    public void reset() {
        getReadCache().clear();
    }

    /**
     * Reads the keys from the underlying data source (which may be a merkle data structure, a
     * fast-copyable data structure, or something else).
     *
     * @param key key to read from state
     * @return The value read from the underlying data source. May be null.
     */
    protected abstract V readFromDataSource(@NonNull K key);

    /**
     * Records the given key and associated value were read.
     *
     * @param key The key
     * @param value The value
     */
    protected final void markRead(@NonNull K key, @Nullable V value) {
        if (value == null) {
            getReadCache().put(key, (V) marker);
        } else {
            getReadCache().put(key, value);
        }
    }

    /**
     * Gets whether this key has been read at some point by this {@link ReadableKVStateBase}.
     *
     * @param key The key.
     * @return Whether it has been read
     */
    protected final boolean hasBeenRead(@NonNull K key) {
        return getReadCache().containsKey(key);
    }

    private Map<Object, Object> getReadCache() {
        return ContractCallContext.get().getReadCacheState(getStateId());
    }
}
