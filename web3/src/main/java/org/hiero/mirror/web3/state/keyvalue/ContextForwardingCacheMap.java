// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.keyvalue;

import com.google.common.collect.ForwardingMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A delegating ConcurrentMap that lazily accesses the per-request cache from ContractCallContext.
 * This allows singleton state beans to use per-request caches, ensuring proper isolation
 * between concurrent requests.
 */
public class ContextForwardingCacheMap extends ForwardingMap<Object, Object> implements ConcurrentMap<Object, Object> {

    private final int stateId;

    public ContextForwardingCacheMap(final int stateId) {
        this.stateId = stateId;
    }

    /**
     * Gets the actual cache map from the ContractCallContext for the current request.
     * If the context is not initialized, returns a temporary empty map.
     */
    @Override
    protected Map<Object, Object> delegate() {
        if (ContractCallContext.isInitialized()) {
            return ContractCallContext.get().getReadCacheState(stateId);
        }
        return new ConcurrentHashMap<>();
    }

    // Implement ConcurrentMap-specific methods
    @Override
    public Object putIfAbsent(final Object key, final Object value) {
        final Map<Object, Object> map = delegate();
        if (map instanceof ConcurrentMap) {
            return map.putIfAbsent(key, value);
        }
        // Fallback for regular Map: check if key exists, if not put it
        if (!map.containsKey(key)) {
            return map.put(key, value);
        }
        return map.get(key);
    }

    @Override
    public boolean remove(final Object key, final Object value) {
        final Map<Object, Object> map = delegate();
        if (map instanceof ConcurrentMap) {
            return map.remove(key, value);
        }

        if (map.containsKey(key) && Objects.equals(map.get(key), value)) {
            map.remove(key);
            return true;
        }
        return false;
    }

    @Override
    public boolean replace(final Object key, final Object oldValue, final Object newValue) {
        final Map<Object, Object> map = delegate();
        if (map instanceof ConcurrentMap) {
            return map.replace(key, oldValue, newValue);
        }

        if (map.containsKey(key) && Objects.equals(map.get(key), oldValue)) {
            map.put(key, newValue);
            return true;
        }
        return false;
    }

    @Override
    public Object replace(final Object key, final Object value) {
        final Map<Object, Object> map = delegate();
        if (map instanceof ConcurrentMap) {
            return map.replace(key, value);
        }

        return map.containsKey(key) ? map.put(key, value) : null;
    }
}
