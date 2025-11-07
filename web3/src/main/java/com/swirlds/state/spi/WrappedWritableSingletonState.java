// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import java.util.Map;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;

/**
 * An implementation of {@link WritableSingletonState} that delegates to another {@link WritableSingletonState} as
 * though it were the backend data source. Modifications to this {@link WrappedWritableSingletonState} are
 * buffered, along with reads, allowing code to rollback by simply throwing away the wrapper.
 *
 * @param <T> the type of the state
 */
@SuppressWarnings("unchecked")
public class WrappedWritableSingletonState<T> extends WritableSingletonStateBase<T> {

    private final WritableSingletonState<T> delegate;

    /**
     * Create a new instance that will treat the given {@code delegate} as the backend data source.
     * Note that the lifecycle of the delegate <b>MUST</b> be as long as, or longer than, the
     * lifecycle of this instance. If the delegate is reset or decommissioned while being used as a
     * delegate, bugs will occur.
     *
     * @param delegate The delegate. Must not be null.
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public WrappedWritableSingletonState(@NonNull final WritableSingletonState<T> delegate) {
        super(delegate.getStateId(), null);
        this.delegate = delegate;
    }

    @Override
    protected void putIntoDataSource(@NonNull T value) {
        // No-op as we don't persist updates in web3.
    }

    @Override
    protected void removeFromDataSource() {
        // No-op as we don't persist updates in web3.
    }

    @Override
    protected T readFromDataSource() {
        return delegate.get();
    }

    @Override
    public void put(T value) {
        getWriteCache().put(stateId, value);
    }

    @Override
    public T get() {
        final var writeCache = getWriteCache();
        final var readCache = getReadCache();
        if (writeCache.containsKey(stateId)) {
            return (T) writeCache.get(stateId);
        } else if (readCache.containsKey(stateId)) {
            return (T) readCache.get(stateId);
        } else {
            final var value = readFromDataSource();
            readCache.put(stateId, value);
            return value;
        }
    }

    @Override
    public boolean isModified() {
        return getWriteCache().containsKey(stateId);
    }

    @Override
    public boolean isRead() {
        return getReadCache().containsKey(stateId);
    }

    @Override
    public void reset() {
        // No-op
    }

    @Override
    public void commit() {
        // No-op
    }

    private Map<Object, Object> getReadCache() {
        return ContractCallContext.get().getReadCacheState(stateId);
    }

    private Map<Object, Object> getWriteCache() {
        return ContractCallContext.get().getWriteCacheState(stateId);
    }
}
