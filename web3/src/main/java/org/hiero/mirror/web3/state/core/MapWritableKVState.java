// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import java.util.List;
import java.util.Objects;
import org.hiero.mirror.web3.ApiEndpointName;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;

@SuppressWarnings("deprecation")
public class MapWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final ReadableKVState<K, V> readableBackingStore;

    public MapWritableKVState(
            @NonNull final String serviceName,
            final int stateId,
            @NonNull final ReadableKVState<K, V> readableBackingStore) {
        super(serviceName, stateId);
        this.readableBackingStore = Objects.requireNonNull(readableBackingStore);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected V readFromDataSource(@NonNull K key) {
        final var committed = ContractCallContext.get().getCommittedCacheState(getStateId());
        if (committed.containsKey(key)) {
            return (V) committed.get(key);
        }
        return readableBackingStore.get(key);
    }

    /** Retains successful simulation writes for later transactions in this actions request only. */
    @Override
    @SuppressWarnings("unchecked")
    public void commit() {
        if (!ContractCallContext.isInitialized()) {
            return;
        }
        final var context = ContractCallContext.get();
        if (context.getApi() != ApiEndpointName.ACTIONS) {
            return;
        }
        final var keys = List.copyOf(context.getWriteCacheState(getStateId()).keySet());
        context.commitWrites(getStateId());
        final var committed = context.getCommittedCacheState(getStateId());
        for (final var key : keys) {
            markRead((K) key, (V) committed.get(key));
        }
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
    protected boolean useRequestWriteCache() {
        return true;
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
