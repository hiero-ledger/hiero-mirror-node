// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.google.common.collect.ForwardingConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A ForwardingMap that lazily delegates operations to the read cache from ContractCallContext.
 * This allows singleton state beans to use per-request read caches, ensuring proper isolation
 * between concurrent requests.
 */
public class ForwardingReadableKVStateBase<K, V> extends ForwardingConcurrentMap<K, V> {

    private static final ConcurrentMap<Object, Object> EMPTY_MAP = new ConcurrentHashMap<>();

    private final int stateId;

    public ForwardingReadableKVStateBase(final int stateId) {
        this.stateId = stateId;
    }

    /**
     * Gets the actual cache map from the ContractCallContext for the current request.
     * If the context is not initialized, returns a temporary empty map.
     */
    @Override
    protected ConcurrentMap<K, V> delegate() {
        if (!ContractCallContext.isInitialized()) {
            return (ConcurrentMap<K, V>) EMPTY_MAP;
        }
        return (ConcurrentMap<K, V>) ContractCallContext.get().getReadCacheState(stateId);
    }
}
