// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static java.util.Objects.requireNonNull;

import com.swirlds.state.spi.KVChangeListener;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.hiero.mirror.web3.common.ContractCallContext;

@SuppressWarnings({"deprecation", "unchecked"})
public class MapWritableKVState<K, V> implements WritableKVState<K, V> {

    private final String stateKey;
    private final ReadableKVState<K, V> readableBackingStore;
    private final List<KVChangeListener<K, V>> listeners = new ArrayList<>();

    public MapWritableKVState(
            @Nonnull final String stateKey, @Nonnull final ReadableKVState<K, V> readableBackingStore) {
        this.stateKey = stateKey;
        this.readableBackingStore = Objects.requireNonNull(readableBackingStore);
    }

    @Nullable
    @Override
    public V getOriginalValue(@Nonnull K key) {
        return readableBackingStore.get(key);
    }

    @Override
    public void put(@Nonnull K key, @Nonnull V value) {
        getWriteCacheState().put(key, value);
    }

    @Override
    public void remove(@Nonnull K key) {
        getWriteCacheState().remove(key);
    }

    @Nonnull
    @Override
    public String getStateKey() {
        return stateKey;
    }

    @Nullable
    @Override
    public V get(@Nonnull K key) {
        final var modifications = getWriteCacheState();
        if (modifications.containsKey(key)) {
            return modifications.get(key);
        } else {
            return readableBackingStore.get(key);
        }
    }

    @Nonnull
    @Override
    public Iterator<K> keys() {
        return getWriteCacheState().keySet().iterator();
    }

    @Nonnull
    @Override
    public Set<K> readKeys() {
        return getWriteCacheState().keySet();
    }

    @Override
    public long size() {
        return getWriteCacheState().size();
    }

    @Nonnull
    @Override
    public Set<K> modifiedKeys() {
        return getWriteCacheState().keySet();
    }

    public void registerListener(@Nonnull final KVChangeListener<K, V> listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    public void commit() {
        final var modifications = getWriteCacheState();
        for (final var entry : modifications.entrySet()) {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value == null) {
                listeners.forEach(listener -> listener.mapDeleteChange(key));
            } else {
                listeners.forEach(listener -> listener.mapUpdateChange(key, value));
            }
        }
    }

    @Override
    public String toString() {
        return "MapWritableKVState{" + "readableBackingStore=" + readableBackingStore + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapWritableKVState<?, ?> that = (MapWritableKVState<?, ?>) o;
        return Objects.equals(getStateKey(), that.getStateKey())
                && Objects.equals(readableBackingStore, that.readableBackingStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateKey(), readableBackingStore);
    }

    private Map<K, V> getWriteCacheState() {
        return ContractCallContext.get().getWriteCacheState(getStateKey());
    }
}
