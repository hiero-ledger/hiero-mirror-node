/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.state.spi;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("unchecked")
/**
 * A base class for implementations of {@link ReadableKVState} and {@link WritableKVState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public abstract class ReadableKVStateBase<K, V> implements ReadableKVState<K, V> {
    /** The state key, which cannot be null */
    private final String stateKey;

    /**
     * A cache of all values read from this {@link ReadableKVState}. If the same value is read
     * twice, rather than going to the underlying merkle data structures to read the data a second
     * time, we simply return it from the cache. We also keep track of all keys read, which is
     * critical for dealing with validating what we read during pre-handle with what may have
     * changed before we got to handle transaction. If the value is "null", this means it was NOT
     * FOUND when we looked it up.
     */
    private ConcurrentMap<K, V> readCache = new ConcurrentHashMap<>();

    private static final Object marker = new Object();

    /**
     * Create a new StateBase.
     *
     * @param stateKey The state key. Cannot be null.
     */
    protected ReadableKVStateBase(@Nonnull String stateKey) {
        this.stateKey = Objects.requireNonNull(stateKey);
    }

    /**
     * Create a new StateBase.
     *
     * @param stateKey The state key. Cannot be null.
     */
    protected ReadableKVStateBase(@Nonnull String stateKey, ConcurrentMap<K, V> readCacheSource) {
        this.stateKey = Objects.requireNonNull(stateKey);
        this.readCache = readCacheSource;
    }

    /** {@inheritDoc} */
    @Override
    @Nonnull
    public final String getStateKey() {
        return stateKey;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public V get(@Nonnull K key) {
        // We need to cache the item because somebody may perform business logic basic on this
        // contains call, even if they never need the value itself!
        Objects.requireNonNull(key);
        if (!hasBeenRead(key)) {
            final var value = readFromDataSource(key);
            markRead(key, value);
        }
        final var value = readCache.get(key);
        return (value == marker) ? null : value;
    }

    /**
     * Gets the set of keys that a client read from the {@link ReadableKVState}.
     *
     * @return The possibly empty set of keys.
     */
    @Nonnull
    public final Set<K> readKeys() {
        return Collections.unmodifiableSet(readCache.keySet());
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
        readCache.clear();
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
        if (value == null) {
            readCache.put(key, (V) marker);
        } else {
            readCache.put(key, value);
        }
    }

    /**
     * Gets whether this key has been read at some point by this {@link ReadableKVStateBase}.
     *
     * @param key The key.
     * @return Whether it has been read
     */
    protected final boolean hasBeenRead(@Nonnull K key) {
        return readCache.containsKey(key);
    }
}
