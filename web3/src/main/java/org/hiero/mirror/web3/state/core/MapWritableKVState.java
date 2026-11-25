// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@SuppressWarnings("deprecation")
public class MapWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final ReadableKVState<K, V> readableBackingStore;

    public MapWritableKVState(
            @NonNull final String serviceName,
            final int stateId,
            @NonNull final ReadableKVState<K, V> readableBackingStore) {
        super(stateId, serviceName, RequestCache.write(stateId), RequestCache.read(stateId));
        this.readableBackingStore = Objects.requireNonNull(readableBackingStore);
    }

    /** Nothing is flushed: the write cache of the request is where the modifications of this state are kept. */
    @Override
    public void commit() {
        // No-op as we don't persist updates in web3.
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        return readableBackingStore.get(key);
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MapWritableKVState<?, ?> that = (MapWritableKVState<?, ?>) o;
        return Objects.equals(getStateId(), that.getStateId())
                && Objects.equals(readableBackingStore, that.readableBackingStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateId(), readableBackingStore);
    }
}
