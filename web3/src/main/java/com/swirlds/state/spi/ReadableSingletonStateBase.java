// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import java.util.Map;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.Nullable;

/**
 * A convenient implementation of {@link ReadableSingletonState}. Copy of the class from hedera-app. The difference is
 * that the read flag and cached value are stored in the per-request {@link ContractCallContext} rather than on this
 * process-wide instance.
 *
 * @param <T> The type of the value
 */
@SuppressWarnings("unchecked")
public abstract class ReadableSingletonStateBase<T> implements ReadableSingletonState<T> {

    /** Sentinel key for the singleton entry in the request-scoped read cache. */
    private static final Object SINGLETON_KEY = new Object();

    /** Marker stored when the backing supplier returns null. */
    private static final Object NULL_MARKER = new Object();

    protected final int stateId;

    /** State label used in logs, typically serviceName.stateKey */
    protected final String label;

    /**
     * Creates a new instance.
     *
     * @param stateId The state ID for this instance.
     * @param label The state label
     */
    public ReadableSingletonStateBase(final int stateId, final String label) {
        this.stateId = stateId;
        this.label = label;
    }

    @Override
    public final int getStateId() {
        return stateId;
    }

    @Override
    @Nullable
    public T get() {
        final var cache = getReadCache();
        final var cached = cache.get(SINGLETON_KEY);
        if (cached != null) {
            return cached == NULL_MARKER ? null : (T) cached;
        }
        final var value = readFromDataSource();
        cache.put(SINGLETON_KEY, value == null ? NULL_MARKER : value);
        return value;
    }

    /**
     * Reads the data from the underlying data source (which may be a merkle data structure, a
     * fast-copyable data structure, or something else).
     *
     * @return The value read from the underlying data source. May be null.
     */
    @Nullable
    protected abstract T readFromDataSource();

    @Override
    public boolean isRead() {
        return getReadCache().containsKey(SINGLETON_KEY);
    }

    /** Clears any cached data, including whether the instance has been read. */
    public void reset() {
        getReadCache().clear();
    }

    private Map<Object, Object> getReadCache() {
        return ContractCallContext.get().getReadCacheState(getStateId());
    }
}
