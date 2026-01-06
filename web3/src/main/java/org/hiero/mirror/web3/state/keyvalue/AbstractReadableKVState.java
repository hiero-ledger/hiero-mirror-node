// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.swirlds.state.spi.ReadableKVStateBase;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.NonNull;

public abstract class AbstractReadableKVState<K, V> extends ReadableKVStateBase<K, V> {

    protected AbstractReadableKVState(@NonNull String serviceName, int stateId) {
        super(stateId, serviceName, (ConcurrentMap<K, V>) new ContextForwardingCacheMap(stateId));
    }

    protected AbstractReadableKVState(@NonNull String serviceName, int stateId, Map<Object, Object> readCache) {
        super(stateId, serviceName, (ConcurrentMap<K, V>) readCache);
    }

    @NonNull
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
