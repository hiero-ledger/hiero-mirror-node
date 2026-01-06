// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.google.common.collect.ForwardingMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A delegating Map that lazily accesses the per-request write cache from ContractCallContext.
 * This allows singleton state beans to use per-request write caches, ensuring proper isolation
 * between concurrent requests.
 */
class ContextFowardingWriteCacheMap extends ForwardingMap<Object, Object> {

    private final int stateId;

    ContextFowardingWriteCacheMap(final int stateId) {
        this.stateId = stateId;
    }

    /**
     * Gets the actual write cache map from the ContractCallContext for the current request.
     * If the context is not initialized, returns a temporary empty map.
     */
    @Override
    protected Map<Object, Object> delegate() {
        if (ContractCallContext.isInitialized()) {
            return ContractCallContext.get().getWriteCacheState(stateId);
        }
        // Return a temporary map if context is not initialized (e.g., during bean initialization)
        // This should not happen during actual request processing
        return new ConcurrentHashMap<>();
    }
}
