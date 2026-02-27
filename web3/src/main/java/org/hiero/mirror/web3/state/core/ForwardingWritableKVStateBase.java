// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.google.common.collect.ForwardingMap;
import java.util.HashMap;
import java.util.Map;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A ForwardingMap that lazily delegates operations to the write cache from ContractCallContext.
 * This allows singleton state beans to use per-request write caches, ensuring proper isolation
 * between concurrent requests.
 */
class ForwardingWritableKVStateBase<K, V> extends ForwardingMap<K, V> {

    private static final Map<Object, Object> EMPTY_MAP = new HashMap<>();

    private final int stateId;

    ForwardingWritableKVStateBase(final int stateId) {
        this.stateId = stateId;
    }

    /**
     * Gets the actual write cache map from the ContractCallContext for the current request.
     * If the context is not initialized, returns a temporary empty map.
     */
    @Override
    protected Map<K, V> delegate() {
        if (!ContractCallContext.isInitialized()) {
            return (Map<K, V>) EMPTY_MAP;
        }

        return (Map<K, V>) ContractCallContext.get().getWriteCacheState(stateId);
    }
}
