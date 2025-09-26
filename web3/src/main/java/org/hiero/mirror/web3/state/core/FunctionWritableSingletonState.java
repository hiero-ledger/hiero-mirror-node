// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.WritableSingletonStateBase;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FunctionWritableSingletonState<S> extends WritableSingletonStateBase<S> {

    private final Supplier<S> backingStoreAccessor;

    private final Consumer<S> backingStoreMutator;

    /**
     * Creates a new instance.
     *
     * @param serviceName The name of the service that owns the state.
     * @param stateKey The state key for this instance.
     * @param backingStoreAccessor A {@link Supplier} that provides access to the value in the
     *     backing store.
     * @param backingStoreMutator A {@link Consumer} for mutating the value in the backing store.
     */
    public FunctionWritableSingletonState(
            @Nonnull final String serviceName,
            @Nonnull final String stateKey,
            @Nonnull final Supplier<S> backingStoreAccessor,
            @Nonnull final Consumer<S> backingStoreMutator) {
        super(serviceName, stateKey);
        this.backingStoreAccessor = Objects.requireNonNull(backingStoreAccessor);
        this.backingStoreMutator = Objects.requireNonNull(backingStoreMutator);
    }

    @Override
    protected S readFromDataSource() {
        return backingStoreAccessor.get();
    }

    @Override
    protected void putIntoDataSource(@Nonnull S value) {
        backingStoreMutator.accept(value);
    }

    @Override
    protected void removeFromDataSource() {
        backingStoreMutator.accept(null);
    }
}
