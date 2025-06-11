// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Iterator;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.state.core.ReadableCachedForwardingConcurrentMap;

public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    /**
     * Each transaction is executed in its own {@link ContractCallContext}, so each transaction has its own read and
     * write cache and this map delegates to the current transaction's read cache. All the methods from the Map interface,
     * such as: get(), put(), etc. will operate on the map from the scoped read cache.
     */
    protected AbstractReadableKVState(@Nonnull String stateKey) {
        super(stateKey, new ReadableCachedForwardingConcurrentMap<>(stateKey));
    }

    @Nonnull
    @Override
    protected Iterator<K> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    @SuppressWarnings("deprecation")
    public long size() {
        return 0;
    }
}
