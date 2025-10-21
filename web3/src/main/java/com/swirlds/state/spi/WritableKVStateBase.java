// SPDX-License-Identifier: Apache-2.0

package com.swirlds.state.spi;

import static java.util.Objects.requireNonNull;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.hiero.mirror.web3.common.ContractCallContext;

/**
 * A base class for implementations of {@link WritableKVState}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
@SuppressWarnings("unchecked")
public abstract class WritableKVStateBase<K, V> extends ReadableKVStateBase<K, V> implements WritableKVState<K, V> {
    /**
     * A list of listeners to be notified of changes to the state.
     */
    private final List<KVChangeListener<K, V>> listeners = new ArrayList<>();

    /**
     * Create a new StateBase.
     *
     * @param serviceName The name of the service that owns the state. Cannot be null.
     * @param stateKey The state key. Cannot be null.
     */
    protected WritableKVStateBase(@Nonnull final String serviceName, final int stateId) {
        super(stateId, serviceName);
    }

    /**
     * Register a listener to be notified of changes to the state on {@link #commit()}. We do not support unregistering
     * a listener, as the lifecycle of a {@link WritableKVState} is scoped to the set of mutations made to a state in a
     * round; and there is no use case where an application would only want to be notified of a subset of those changes.
     * @param listener the listener to register
     */
    public void registerListener(@Nonnull final KVChangeListener<K, V> listener) {
        requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Flushes all changes into the underlying data store. This method should <strong>ONLY</strong>
     * be called by the code that created the {@link WritableKVStateBase} instance or owns it. Don't
     * cast and commit unless you own the instance!
     */
    public void commit() {
        // Do nothing since we do not want to propagate any changes to the underlying datasource.
    }

    /**
     * {@inheritDoc}
     *
     * <p>Clears the set of modified keys and removed keys. Equivalent semantically to a "rollback"
     * operation.
     */
    @Override
    public final void reset() {
        super.reset();
        getWriteCacheState().clear();
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public final V get(@Nonnull K key) {
        // If there is a modification, then we've already done a "put" or "remove"
        // and should return based on the modification
        final var writeCache = getWriteCacheState();
        if (writeCache.containsKey(key)) {
            return (V) writeCache.get(key);
        } else {
            return super.get(key);
        }
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public V getOriginalValue(@Nonnull K key) {
        return super.get(key);
    }

    /** {@inheritDoc} */
    @Override
    public final void put(@Nonnull final K key, @Nonnull final V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        getWriteCacheState().put(key, value);
    }

    /** {@inheritDoc} */
    @Override
    public final void remove(@Nonnull final K key) {
        Objects.requireNonNull(key);
        getWriteCacheState().put(key, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This is a bit of a pain, because we have to take into account modifications! If a key has
     * been removed, then it must be omitted during iteration. If a key has been added, then it must
     * be added into iteration.
     *
     * @return An iterator that iterates over all known keys.
     */
    @Nonnull
    @Override
    public Iterator<K> keys() {
        // Capture the set of keys that have been removed, and the set of keys that have been added.
        final var removedKeys = new HashSet<K>();
        final var maybeAddedKeys = new HashSet<K>();
        for (final var mod : getWriteCacheState().entrySet()) {
            final var key = mod.getKey();
            final var val = mod.getValue();
            if (val == null) {
                removedKeys.add((K) key);
            } else {
                maybeAddedKeys.add((K) key);
            }
        }

        // Get the iterator from the backing store
        final var backendItr = super.keys();

        // Create and return a special iterator which will only include those keys that
        // have NOT been removed, ARE in the backendItr, and includes keys that HAVE
        // been added (i.e. are not in the backendItr).
        return new KVStateKeyIterator<>(backendItr, removedKeys, maybeAddedKeys);
    }

    /** {@inheritDoc} */
    @Nonnull
    @Override
    public final Set<K> modifiedKeys() {
        return (Set<K>) getWriteCacheState().keySet();
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

        for (final var mod : getWriteCacheState().entrySet()) {
            boolean isPresentInBackingMap = readFromDataSource((K) mod.getKey()) != null;
            boolean isRemovedInMod = mod.getValue() == null;

            if (isPresentInBackingMap && isRemovedInMod) {
                numRemovals++;
            } else if (!isPresentInBackingMap && !isRemovedInMod) {
                numAdditions++;
            }
        }
        return sizeOfBackingMap + numAdditions - numRemovals;
    }

    /**
     * Reads from the underlying data source in such a way as to cause any fast-copyable data
     * structures underneath to make a fast copy.
     *
     * @param key key to read from state
     * @return The value read from the underlying data source. May be null.
     */
    protected abstract V getForModifyFromDataSource(@Nonnull K key);

    /**
     * Puts the given key/value pair into the underlying data source.
     *
     * @param key key to update
     * @param value value to put
     */
    protected abstract void putIntoDataSource(@Nonnull K key, @Nonnull V value);

    /**
     * Removes the given key and implicit value from the underlying data source.
     *
     * @param key key to remove from the underlying data source
     */
    protected abstract void removeFromDataSource(@Nonnull K key);

    /**
     * Returns the size of the underlying data source. This can be a merkle map or a virtual map.
     * @return size of the underlying data source.
     */
    protected abstract long sizeOfDataSource();

    private Map<Object, Object> getWriteCacheState() {
        return ContractCallContext.get().getWriteCacheState(getStateId());
    }

    /**
     * A special iterator which includes all keys in the backend iterator, and all keys that have
     * been added but are not part of the backend iterator, and excludes all keys that have been
     * removed (even if they are in the backend iterator).
     *
     * <p>For each key it gets back from the backing store, it must inspect that key to see if it is
     * in the removedKeys or maybeAddedKeys. If it is in the removedKeys, then we don't return it to
     * the caller, and pump another key from the backing store iterator to check instead. If it is
     * in maybeAddedKeys, then remove it from maybeAddedKeys (since it is clearly not added) and
     * then return it to the caller. At the very end, when the backing store iterator tells us it is
     * out of keys, we start going through everything in maybeAddedKeys.
     *
     * <p>This iterator is not fail-fast.
     *
     * @param <K> The type of key
     */
    private static final class KVStateKeyIterator<K> implements Iterator<K> {
        private final Iterator<K> backendItr;
        private final Set<K> removedKeys;
        private final Set<K> maybeAddedKeys;
        private Iterator<K> addedItr;
        private K next;

        private KVStateKeyIterator(
                @Nonnull final Iterator<K> backendItr,
                @Nonnull final Set<K> removedKeys,
                @Nonnull final Set<K> maybeAddedKeys) {
            this.backendItr = backendItr;
            this.removedKeys = removedKeys;
            this.maybeAddedKeys = maybeAddedKeys;
        }

        @Override
        public boolean hasNext() {
            prepareNext();
            return next != null;
        }

        @Override
        public K next() {
            prepareNext();

            if (next == null) {
                throw new NoSuchElementException();
            }

            final var ret = next;
            next = null;
            return ret;
        }

        private void prepareNext() {
            while (next == null) {
                if (backendItr.hasNext()) {
                    final var candidate = backendItr.next();
                    maybeAddedKeys.remove(candidate);
                    if (removedKeys.contains(candidate)) {
                        continue;
                    }
                    next = candidate;
                    return;
                }

                if (addedItr == null) {
                    addedItr = maybeAddedKeys.iterator();
                }

                if (addedItr.hasNext()) {
                    next = addedItr.next();
                }

                // If we get here, then there is nothing.
                return;
            }
        }
    }
}
