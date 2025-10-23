// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import java.util.Optional;
import org.jspecify.annotations.NonNull;

/** A CachingStateFrame that holds reads (falling through to an upstream cache) and local updates/deletes. */
public class RWCachingStateFrame<K> extends ROCachingStateFrame<K> {

    public RWCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame, @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
    }

    @Override
    public void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        cache.update(key, value);
    }

    @Override
    public void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        cache.delete(key);
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> downstreamFrame) {
        final var thisCaches = this.getInternalCaches();
        final var downstreamCaches = downstreamFrame.getInternalCaches();
        if (thisCaches.size() != downstreamCaches.size()) {
            throw new IllegalStateException("This frame and downstream frame have different klasses registered");
        }
        for (final var kv : thisCaches.entrySet()) {
            if (!downstreamCaches.containsKey(kv.getKey())) {
                throw new IllegalStateException("This frame and downstream frame have different klasses registered");
            }
            kv.getValue().coalesceFrom(downstreamCaches.get(kv.getKey()));
        }
    }
}
