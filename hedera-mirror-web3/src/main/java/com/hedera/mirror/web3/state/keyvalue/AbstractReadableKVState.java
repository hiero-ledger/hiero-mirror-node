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

package com.hedera.mirror.web3.state.keyvalue;

import com.google.common.collect.ForwardingMap;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

@SuppressWarnings("unchecked")
public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    protected AbstractReadableKVState(@Nonnull String stateKey) {
        super(stateKey, new ForwardingMap<>() {
            @Override
            @Nonnull
            protected Map<K, V> delegate() {
                return (Map<K, V>) ContractCallContext.get().getReadCacheState(stateKey);
            }
        });
    }

    protected AbstractReadableKVState(@Nonnull String stateKey, @Nonnull Map<K, V> backingMap) {
        super(stateKey, new ForwardingMap<>() {

            @Override
            @Nonnull
            protected Map<K, V> delegate() {
                return (Map<K, V>) ContractCallContext.get().getReadCacheState(stateKey);
            }
        });
        //        ContractCallContext.get().getReadCacheState(stateKey).putAll(backingMap);
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return readKeys().size();
    }
}
