// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.google.common.collect.ForwardingConcurrentMap;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * This map uses the scoped readable cache from {@link ContractCallContext} as un underlying datasource.
 * All calls to this map are forwarded internally to the above mentioned cache.
 */
@SuppressWarnings("unchecked")
public class ReadableCachedForwardingConcurrentMap<K, V> extends ForwardingConcurrentMap<K, V> {

    private final String key;

    public ReadableCachedForwardingConcurrentMap(final String key) {
        this.key = key;
    }

    @Override
    protected ConcurrentHashMap<K, V> delegate() {
        return (ConcurrentHashMap<K, V>) ContractCallContext.get().getReadCacheState(key);
    }

    @Override
    public Set<K> keySet() {
        // On Spring bean initialization we don't have a ContractCallContext yet, so an empty set is passed.
        // Once we are in a running transaction, we need to use its context to handle the cache properly.
        return ContractCallContext.isInitialized()
                ? (Set<K>) ContractCallContext.get().getReadCacheState(key).keySet()
                : Collections.emptySet();
    }
}
