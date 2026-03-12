// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.google.common.collect.ForwardingMap;
import java.util.HashMap;
import java.util.Map;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A ForwardingMap that lazily delegates operations to the read cache from ContractCallContext.
 * This allows singleton state beans to use per-request read caches, ensuring proper isolation
 * between concurrent requests.
 */
public class ForwardingReadableKVStateBase<K, V> extends ForwardingMap<K, V> {

    private static final Map<Object, Object> EMPTY_MAP = new HashMap<>();

    private final int stateId;

    public ForwardingReadableKVStateBase(final int stateId) {
        this.stateId = stateId;
    }

    /**
     * Gets the actual cache map from the ContractCallContext for the current request.
     * If the context is not initialized, returns a temporary empty map.
     */
    @Override
    protected Map<K, V> delegate() {
        if (!ContractCallContext.isInitialized()) {
            return (Map<K, V>) EMPTY_MAP;
        }
        return (Map<K, V>) ContractCallContext.get().getReadCacheState(stateId);
    }
}
