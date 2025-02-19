/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.web3.state.keyvalue.AbstractReadableKVState;
import com.swirlds.state.spi.ReadableKVState;
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
public class MapReadableKVState<K, V> extends AbstractReadableKVState<K, V> {
    /** Represents the backing storage for this state */
    //    private final Map<K, V> backingStore;

    /**
     * Create an instance using the given map as the backing store. This is useful when you want to* pre-populate the map, or if you want to use Mockito to mock it or cause it to throw
     * exceptions when certain keys are accessed, etc.
     *
     * @param stateKey The state key for this state
     * @param backingStore The backing store to use
     */
    public MapReadableKVState(@Nonnull final String stateKey, @Nonnull final Map<K, V> backingStore) {
        super(stateKey, backingStore);
        //        this.backingStore = Objects.requireNonNull(backingStore);
    }

    @Override
    protected V readFromDataSource(@Nonnull K key) {
        return super.get(key);
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        //        return backingStore.keySet().iterator();
        return super.iterateFromDataSource();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapReadableKVState<?, ?> that = (MapReadableKVState<?, ?>) o;
        return Objects.equals(getStateKey(), that.getStateKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStateKey());
    }
}
