// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import java.util.Optional;
import org.hiero.mirror.web3.evm.exception.WrongTypeException;
import org.jspecify.annotations.NonNull;

/** A CachingStateFrame that holds reads (falling through to an upstream cache) and disallows updates/deletes. */
public class ROCachingStateFrame<K> extends CachingStateFrame<K> {

    public ROCachingStateFrame(
            @NonNull final Optional<CachingStateFrame<K>> upstreamFrame, @NonNull final Class<?>... klassesToCache) {
        super(upstreamFrame, klassesToCache);
    }

    @Override
    @NonNull
    public Optional<Object> getValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        final var entry = cache.get(key);
        return switch (entry.state()) {
            case NOT_YET_FETCHED ->
                upstreamFrame.flatMap(upstreamFrame -> {
                    final var upstreamAccessor = upstreamFrame.getAccessor(klass);
                    try {
                        final var upstreamValue = upstreamAccessor.get(key);
                        cache.fill(key, upstreamValue.orElse(null));
                        return upstreamValue;
                    } catch (final WrongTypeException e) {
                        throw new CacheAccessIncorrectTypeException(e.getMessage());
                    }
                });
            case PRESENT, UPDATED -> Optional.of(entry.value());
            case MISSING, DELETED -> Optional.empty();
            case INVALID -> throw new IllegalArgumentException("Trying to get value when state is invalid");
        };
    }

    @Override
    public void setValue(
            @NonNull final Class<?> klass,
            @NonNull final UpdatableReferenceCache<K> cache,
            @NonNull final K key,
            @NonNull final Object value) {
        throw new UnsupportedOperationException("Cannot write value to a R/O cache");
    }

    @Override
    public void deleteValue(
            @NonNull final Class<?> klass, @NonNull final UpdatableReferenceCache<K> cache, @NonNull final K key) {
        throw new UnsupportedOperationException("Cannot delete value from a R/O cache");
    }

    @Override
    public void updatesFromDownstream(@NonNull final CachingStateFrame<K> childFrame) {
        throw new UnsupportedOperationException("Cannot commit to a R/O cache");
    }
}
