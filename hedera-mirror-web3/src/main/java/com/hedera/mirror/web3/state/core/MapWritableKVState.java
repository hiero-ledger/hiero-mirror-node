/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.core;

import com.google.common.collect.ForwardingMap;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;

@SuppressWarnings("unchecked")
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
    public V getForModify(@Nonnull K key) {
        return super.get(key);
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
