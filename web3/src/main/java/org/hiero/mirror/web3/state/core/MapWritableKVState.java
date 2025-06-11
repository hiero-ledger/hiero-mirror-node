// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.google.common.collect.ForwardingMap;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import org.hiero.mirror.web3.common.ContractCallContext;

@SuppressWarnings({"deprecation", "unchecked"})
public class MapWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final ReadableKVState<K, V> readableBackingStore;

    public MapWritableKVState(
            @Nonnull final String stateKey, @Nonnull final ReadableKVState<K, V> readableBackingStore) {
        super(stateKey, new ForwardingMap<>() {
            @Override
            protected HashMap<K, V> delegate() {
                return (HashMap<K, V>) ContractCallContext.get().getWriteCacheState(stateKey);
            }
        });
        this.readableBackingStore = Objects.requireNonNull(readableBackingStore);
    }

    @Override
    protected V readFromDataSource(@Nonnull K key) {
        return readableBackingStore.get(key);
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return readableBackingStore.keys();
    }

    @Override
    protected void putIntoDataSource(@Nonnull K key, @Nonnull V value) {
        put(key, value);
    }

    @Override
    protected void removeFromDataSource(@Nonnull K key) {
        remove(key);
    }

    @Override
    public long sizeOfDataSource() {
        return readableBackingStore.size();
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
}
