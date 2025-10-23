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
     * @param stateKey The state key for this instance.
     * @param backingStoreAccessor A {@link Supplier} that provides access to the value in the
     *     backing store.
     */
    public FunctionWritableSingletonState(
            @NonNull final String serviceName,
            @NonNull final String stateKey,
            @NonNull final Supplier<S> backingStoreAccessor) {
        super(serviceName, stateKey);
        this.backingStoreAccessor = Objects.requireNonNull(backingStoreAccessor);
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
