// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import com.google.common.collect.ForwardingConcurrentMap;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    protected AbstractReadableKVState(@Nonnull String stateKey) {
        super(stateKey, new ForwardingConcurrentMap<>() {
            @Override
            protected ConcurrentHashMap<K, V> delegate() {
                return (ConcurrentHashMap<K, V>) ContractCallContext.get().getReadCacheState(stateKey);
            }

            @Override
            public Set<K> keySet() {
                return ContractCallContext.isInitialized()
                        ? (Set<K>) ContractCallContext.get()
                                .getReadCacheState(stateKey)
                                .keySet()
                        : Collections.emptySet();
            }
        });
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    @SuppressWarnings("deprecation")
    public long size() {
        return ContractCallContext.get().getReadCacheState(getStateKey()).size();
    }
}
