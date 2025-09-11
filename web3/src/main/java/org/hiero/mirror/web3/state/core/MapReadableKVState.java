// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * A simple implementation of {@link ReadableKVState} backed by a
 * {@link Map}. Test code has the option of creating an instance disregarding the backing map, or by
 * supplying the backing map to use. This latter option is useful if you want to use Mockito to spy
 * on it, or if you want to pre-populate it, or use Mockito to make the map throw an exception in
 * some strange case, or in some other way work with the backing map directly.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class MapReadableKVState<K, V> extends ReadableKVStateBase<K, V> {
    /** Represents the backing storage for this state */
    private final Map<K, V> backingStore;

    /**
     * Create an instance using the given map as the backing store. This is useful when you want to
     * pre-populate the map, or if you want to use Mockito to mock it or cause it to throw
     * exceptions when certain keys are accessed, etc.
     *
     * @param stateKey The state key for this state
     * @param backingStore The backing store to use
     */
    public MapReadableKVState(
            @Nonnull final String serviceName, @Nonnull final String stateKey, @Nonnull final Map<K, V> backingStore) {
        super(serviceName, stateKey);
        this.backingStore = Objects.requireNonNull(backingStore);
    }

    @Override
    protected V readFromDataSource(@Nonnull K key) {
        return backingStore.get(key);
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return backingStore.keySet().iterator();
    }

    @Override
    @SuppressWarnings("deprecation")
    public long size() {
        return backingStore.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapReadableKVState<?, ?> that = (MapReadableKVState<?, ?>) o;
        return Objects.equals(getStateKey(), that.getStateKey()) && Objects.equals(backingStore, that.backingStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateKey(), backingStore);
    }
}
