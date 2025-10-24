// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableSingletonStateBase;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

public class FunctionReadableSingletonState<S> extends ReadableSingletonStateBase<S> {

    private final Supplier<S> backingStoreAccessor;

    /**
     * Creates a new instance.
     *
     * @param serviceName The name of the service that owns the state.
     * @param stateKey The state key for this instance.
     * @param backingStoreAccessor A {@link Supplier} that provides access to the value in the
     *     backing store.
     */
    public FunctionReadableSingletonState(
            @NonNull final String serviceName, final int stateId, @NonNull final Supplier<S> backingStoreAccessor) {
        super(stateId, serviceName);
        this.backingStoreAccessor = Objects.requireNonNull(backingStoreAccessor);
    }

    @Override
    protected S readFromDataSource() {
        return backingStoreAccessor.get();
    }
}
