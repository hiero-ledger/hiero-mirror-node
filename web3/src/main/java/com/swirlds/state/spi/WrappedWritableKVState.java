// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import java.io.Serial;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.NonNull;

/**
 * An implementation of {@link WritableKVState} that delegates to another {@link WritableKVState} as though it were the
 * backend data source. Copy of the class from swirlds-state-api, which this one precedes on the classpath. The
 * difference is the read cache: a wrapper belongs to a savepoint of one request and is used by the thread of that
 * request only, so it is a {@link HashMap} instead of the {@link java.util.concurrent.ConcurrentHashMap} the base class
 * creates by default. This change removes the overhead of working with a concurrent collection at each savepoint.
 *
 * @param <K> The key
 * @param <V> The value
 */
public class WrappedWritableKVState<K, V> extends WritableKVStateBase<K, V> {

    private final WritableKVState<K, V> delegate;

    public WrappedWritableKVState(@NonNull final WritableKVState<K, V> delegate) {
        // No label, as in the copied class
        super(delegate.getStateId(), null, new LinkedHashMap<>(), new ReadCache<>());
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public long sizeOfDataSource() {
        return delegate.size();
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        delegate.put(key, value);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        return delegate.get(key);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        delegate.remove(key);
    }

    /** A {@link HashMap} that can be passed as the {@link ConcurrentMap} the base class requires for its read cache. */
    private static final class ReadCache<K, V> extends HashMap<K, V> implements ConcurrentMap<K, V> {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
