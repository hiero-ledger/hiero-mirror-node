// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A base class for implementations of {@link ReadableKVState} and {@link WritableKVState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
@SuppressWarnings("unchecked")
public abstract class ReadableKVStateBase<K, V> implements ReadableKVState<K, V> {

    /** State label used in logs, typically serviceName.stateKey (may be null) */
    @Nullable
    protected final String label;

    // The state ID
    protected final int stateId;

    private static final Object marker = new Object();

    /**
     * Create a new StateBase.
     *
     * @param stateId The state ID
     * @param label The state label (may be null)
     */
    protected ReadableKVStateBase(final int stateId, @Nullable final String label) {
        this.label = label; // allow null to match platform-sdk behavior
        this.stateId = stateId;
    }

    /** {@inheritDoc} */
    @Override
    @Nonnull
    public final int getStateId() {
        return stateId;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public V get(@Nonnull K key) {
        // We need to cache the item because somebody may perform business logic basic on this
        // contains call, even if they never need the value itself!
        if (key == null) {
            throw new NullPointerException("key must not be null");
        }
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
    @Nonnull
    public final Set<K> readKeys() {
        return (Set<K>) getReadCache().keySet();
    }

    /** {@inheritDoc} */
    @Nonnull
    @Override
    public Iterator<K> keys() {
        return iterateFromDataSource();
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
    protected abstract V readFromDataSource(@Nonnull K key);

    /**
     * Gets an iterator from the data source that iterates over all keys.
     *
     * @return An iterator over all keys in the data source.
     */
    @Nonnull
    protected abstract Iterator<K> iterateFromDataSource();

    /**
     * Records the given key and associated value were read.
     *
     * @param key The key
     * @param value The value
     */
    protected final void markRead(@Nonnull K key, @Nullable V value) {
        getReadCache().put(key, value == null ? (V) marker : value);
    }

    /**
     * Gets whether this key has been read at some point by this {@link ReadableKVStateBase}.
     *
     * @param key The key.
     * @return Whether it has been read
     */
    protected final boolean hasBeenRead(@Nonnull K key) {
        return getReadCache().containsKey(key);
    }

    private Map<Object, Object> getReadCache() {
        return ContractCallContext.get().getReadCacheState(getStateId());
    }
}
