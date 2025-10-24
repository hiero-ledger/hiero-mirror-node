// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

/** A CachingStateFrame that answers "missing" for all reads and disallows all updates/writes. */
public class BottomCachingStateFrame<K> extends CachingStateFrame<K> {

    public BottomCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame, @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
        upstreamFrame.ifPresent(dummy -> {
            throw new UnsupportedOperationException("bottom cache can not have an upstream cache");
        });
    }

    @Override
    public @NonNull Optional<Object> getValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        return Optional.empty();
    }

    @Override
    public void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        throw new UnsupportedOperationException("cannot write to a bottom cache");
    }

    @Override
    public void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        throw new UnsupportedOperationException("cannot delete from a bottom cache");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
        Objects.requireNonNull(childFrame, "childFrame");
        // ok to commit but does nothing
    }
}
