// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.WritableSingletonStateBase;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

public class FunctionWritableSingletonState<S> extends WritableSingletonStateBase<S> {

    private final Supplier<S> backingStoreAccessor;

    /**
     * Creates a new instance.
     *
     * @param serviceName The name of the service that owns the state.
     * @param stateId The state id for this instance.
     * @param backingStoreAccessor A {@link Supplier} that provides access to the value in the
     *     backing store.
     */
    public FunctionWritableSingletonState(
            @NonNull final String serviceName, final int stateId, @NonNull final Supplier<S> backingStoreAccessor) {
        super(stateId, serviceName);
        this.backingStoreAccessor = Objects.requireNonNull(backingStoreAccessor);
    }

    /** Nothing is flushed: the write cache of the request is where the modification of this state is kept. */
    @Override
    public void commit() {
        // No-op as we don't persist updates in web3.
    }

    /** Unlike hedera-app, falls back to the value read when the state was modified to null, since callers expect a value. */
    @Override
    public S get() {
        final S modification = SingletonRequestCache.modification(stateId);
        return modification != null ? modification : SingletonRequestCache.read(stateId, backingStoreAccessor);
    }

    @Override
    public boolean isModified() {
        return SingletonRequestCache.isModified(stateId);
    }

    @Override
    public boolean isRead() {
        return SingletonRequestCache.isRead(stateId);
    }

    @Override
    public void put(final S value) {
        SingletonRequestCache.modify(stateId, value);
    }

    @Override
    public void reset() {
        SingletonRequestCache.resetModification(stateId);
        SingletonRequestCache.resetRead(stateId);
    }

    @Override
    protected S readFromDataSource() {
        return backingStoreAccessor.get();
    }

    @Override
    protected void putIntoDataSource(@NonNull S value) {
        // No-op as we don't persist updates in web3.
    }

    @Override
    protected void removeFromDataSource() {
        // No-op as we don't persist updates in web3.
    }
}
