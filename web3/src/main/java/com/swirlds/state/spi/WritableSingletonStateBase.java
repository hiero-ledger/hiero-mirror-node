// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A convenient base class for mutable singletons.
 * Copy of the class from hedera-app. Differences: get() does not return null values, and the modification buffer is
 * stored in the per-request {@link ContractCallContext} rather than on this process-wide instance.
 * @param <T> The type
 */
@SuppressWarnings("unchecked")
public abstract class WritableSingletonStateBase<T> extends ReadableSingletonStateBase<T>
        implements WritableSingletonState<T> {

    /**
     * A sentinel value to represent null in the backing store.
     */
    private static final Object NULL_VALUE = new Object();

    /** Sentinel key for the singleton entry in the request-scoped write cache. */
    private static final Object SINGLETON_KEY = new Object();

    /** A list of listeners to be notified of changes to the state */
    private final List<SingletonChangeListener<T>> listeners = new ArrayList<>();

    /**
     * Creates a new instance.
     *
     * @param stateId The state ID for this instance
     * @param label The state label
     */
    public WritableSingletonStateBase(final int stateId, final String label) {
        super(stateId, label);
    }

    /**
     * Register a listener to be notified of changes to the state on {@link #commit()}. We do not support unregistering
     * a listener, as the lifecycle of a {@link WritableSingletonState} is scoped to the set of mutations made to a
     * state in a round; and there is no case where an application would only want to be notified of a subset of those
     * changes.
     *
     * @param listener the listener to register
     */
    public void registerListener(@NonNull final SingletonChangeListener<T> listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    @Override
    @Nullable
    public T get() {
        // If there is a modification, then we've already done a "put" or "remove"
        // and should return based on the modification
        if (isModified()) {
            // The change from the copied class is here - preventing null values
            // as they cause NullPointerExceptions in some various places in the code.
            final var currentValue = currentValue();
            return currentValue != null ? currentValue : super.get();
        }
        return super.get();
    }

    @Override
    public void put(@Nullable final T value) {
        getWriteCacheState().put(SINGLETON_KEY, value == null ? NULL_VALUE : value);
    }

    @Override
    public boolean isModified() {
        return getWriteCacheState().containsKey(SINGLETON_KEY);
    }

    /**
     * Flushes all changes into the underlying data store. This method should <strong>ONLY</strong>
     * be called by the code that created the {@link WritableSingletonStateBase} instance or owns
     * it. Don't cast and commit unless you own the instance!
     *
     * <p>Does not {@link #reset()} the request-scoped write cache. Hedera savepoint wrappers
     * ({@code WrappedWritableSingletonState}) share this cache by {@code stateId}; clearing on
     * commit would drop increments (e.g. entity IDs) needed by nested contract creates. Rollback
     * is {@link #reset()} or {@link ContractCallContext#reset()}.
     */
    public void commit() {
        if (isModified()) {
            if (currentValue() != null) {
                putIntoDataSource(currentValue());
                //noinspection DataFlowIssue
                listeners.forEach(l -> l.singletonUpdateChange(currentValue()));
            } else {
                removeFromDataSource();
            }
        }
    }

    @Nullable
    private T currentValue() {
        final var cached = getWriteCacheState().get(SINGLETON_KEY);
        return cached == NULL_VALUE ? null : (T) cached;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the "modified" and cached value, in addition to the super implementation
     */
    @Override
    public void reset() {
        getWriteCacheState().clear();
        super.reset();
    }

    /**
     * Puts the given value into the underlying data source.
     *
     * @param value value to put
     */
    protected abstract void putIntoDataSource(@NonNull T value);

    /**
     * Removes the value related to this singleton from the underlying data source.
     */
    protected abstract void removeFromDataSource();

    private Map<Object, Object> getWriteCacheState() {
        return ContractCallContext.get().getWriteCacheState(getStateId());
    }
}
