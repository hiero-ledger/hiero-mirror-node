// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.WritableSingletonStateBase;
import java.util.Objects;
import org.hiero.mirror.web3.state.singleton.SingletonState;
import org.jspecify.annotations.NonNull;

public class FunctionWritableSingletonState<S> extends WritableSingletonStateBase<S> {

    private final SingletonState<S> backingStore;

    /**
     * Creates a new instance.
     *
     * @param serviceName The name of the service that owns the state.
     * @param stateId The state id for this instance.
     * @param backingStore The {@link SingletonState} that provides access to the value in the backing store.
     */
    public FunctionWritableSingletonState(
            @NonNull final String serviceName, final int stateId, @NonNull final SingletonState<S> backingStore) {
        super(stateId, serviceName);
        this.backingStore = Objects.requireNonNull(backingStore);
    }

    @Override
    protected S readFromDataSource() {
        return backingStore.get();
    }

    @Override
    protected void putIntoDataSource(@NonNull S value) {
        backingStore.onCommit(value);
    }

    @Override
    protected void removeFromDataSource() {
        // No-op as we don't persist updates in web3.
    }
}
