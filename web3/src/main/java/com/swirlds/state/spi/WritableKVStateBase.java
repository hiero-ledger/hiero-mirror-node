// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A base class for implementations of {@link WritableKVState}.
 *
 * <p>Process-wide states ({@link #useRequestWriteCache()}) stage writes in the request-scoped
 * {@link ContractCallContext}. Savepoint wrappers keep a private map, so dropping the wrapper discards an uncommitted
 * frame. A wrapper {@link #commit()} pushes that frame into its delegate.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public abstract class WritableKVStateBase<K, V> extends ReadableKVStateBase<K, V> implements WritableKVState<K, V> {

    /** Modifications for one savepoint wrapper. Request-scoped states use {@link ContractCallContext} instead. */
    private final Map<K, V> localModifications = new LinkedHashMap<>();

    /**
     * Create a new StateBase.
     *
     * @param serviceName The name of the service that owns the state. Cannot be null.
     * @param stateId The state id.
     */
    protected WritableKVStateBase(@NonNull final String serviceName, final int stateId) {
        super(stateId, serviceName);
    }

    /**
     * Compatibility constructor matching platform-sdk signature (int, String).
     * Some components (e.g., WrappedWritableKVState) expect this exact signature at runtime.
     *
     * @param stateId The state ID
     * @param label The state label (may be null)
     */
    protected WritableKVStateBase(final int stateId, final String label) {
        super(stateId, label);
    }

    /**
     * Flushes this frame into its delegate. Request-scoped states leave their staged writes in
     * {@link ContractCallContext} and override this method when those writes should be published. A savepoint wrapper
     * pushes its private modifications to the parent. This method should <strong>ONLY</strong> be called by the code
     * that created the {@link WritableKVStateBase} instance or owns it.
     */
    public void commit() {
        if (useRequestWriteCache()) {
            return;
        }
        for (final var entry : localModifications.entrySet()) {
            final K key = entry.getKey();
            final V value = entry.getValue();
            if (value == null) {
                removeFromDataSource(key);
            } else {
                putIntoDataSource(key, value);
            }
        }
        localModifications.clear();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the set of modified keys and removed keys. Equivalent semantically to a "rollback"
     * operation. A request-scoped state also drops its read cache. A savepoint wrapper clears only its private
     * modifications, so a nested rollback does not wipe the parent frame.
     */
    @Override
    public final void reset() {
        if (useRequestWriteCache()) {
            getWriteCacheState().clear();
            super.reset();
        } else {
            localModifications.clear();
        }
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public final V get(@NonNull K key) {
        // If there is a modification, then we've already done a "put" or "remove"
        // and should return based on the modification
        final var mods = modifications();
        if (mods.containsKey(key)) {
            return mods.get(key);
        }
        // A wrapper must not answer from the shared read cache. That cache is shared with the root and the database
        // readable state, so a hit would hide a parent frame's uncommitted put.
        if (!useRequestWriteCache()) {
            return readFromDataSource(key);
        }
        return super.get(key);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public V getOriginalValue(@NonNull K key) {
        return super.get(key);
    }

    /** {@inheritDoc} */
    @Override
    public final void put(@NonNull final K key, @NonNull final V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        modifications().put(key, value);
    }

    /** {@inheritDoc} */
    @Override
    public final void remove(@NonNull final K key) {
        Objects.requireNonNull(key);
        modifications().put(key, null);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public final Set<K> modifiedKeys() {
        return modifications().keySet();
    }

    /**
     * {@inheritDoc}
     * For the size of a {@link WritableKVState}, we need to take into account the size of the
     * underlying data source, and the modifications that have been made to the state.
     * <ol>
     * <li>if the key is in backing store and is removed in modifications, then it is counted as removed</li>
     * <li>if the key is not in backing store and is added in modifications, then it is counted as addition</li>
     * <li>if the key is in backing store and is added in modifications, then it is not counted as the
     * key already exists in state</li>
     * <li>if the key is not in backing store and is being tried to be removed in modifications,
     * then it is not counted as the key does not exist in state.</li>
     * </ol>
     * @return The size of the state.
     */
    @Deprecated
    public long size() {
        final var sizeOfBackingMap = sizeOfDataSource();
        int numAdditions = 0;
        int numRemovals = 0;

        for (final var mod : modifications().entrySet()) {
            final boolean isPresentInBackingMap = readFromDataSource(mod.getKey()) != null;
            final boolean isRemovedInMod = mod.getValue() == null;

            if (isPresentInBackingMap && isRemovedInMod) {
                numRemovals++;
            } else if (!isPresentInBackingMap && !isRemovedInMod) {
                numAdditions++;
            }
        }
        return sizeOfBackingMap + numAdditions - numRemovals;
    }

    /**
     * Puts the given key/value pair into the underlying data source.
     *
     * @param key key to update
     * @param value value to put
     */
    protected abstract void putIntoDataSource(@NonNull K key, @NonNull V value);

    /**
     * Removes the given key and implicit value from the underlying data source.
     *
     * @param key key to remove from the underlying data source
     */
    protected abstract void removeFromDataSource(@NonNull K key);

    /**
     * Returns the size of the underlying data source. This can be a merkle map or a virtual map.
     * @return size of the underlying data source.
     */
    protected abstract long sizeOfDataSource();

    /**
     * Process-wide states stage every request's writes in {@link ContractCallContext}. Savepoint wrappers return false
     * and keep {@link #localModifications}.
     */
    protected boolean useRequestWriteCache() {
        return false;
    }

    private Map<Object, Object> getWriteCacheState() {
        return ContractCallContext.get().getWriteCacheState(getStateId());
    }

    @SuppressWarnings("unchecked")
    private Map<K, V> modifications() {
        if (useRequestWriteCache()) {
            return (Map<K, V>) (Map<?, ?>) getWriteCacheState();
        }
        return localModifications;
    }
}
