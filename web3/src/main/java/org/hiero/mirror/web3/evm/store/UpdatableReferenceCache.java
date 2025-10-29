// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState;
import org.hiero.mirror.web3.evm.store.impl.UpdatableReferenceCacheLineState.Entry;
import org.jspecify.annotations.NonNull;

/**
 * Runs a cache for references but treated as values, for use as part of a
 * _stacked_ cache with _commit_/_rollback_ to lower levels.
 *
 * Keeps track of current value of k/v pair, including if the key has been
 * deleted.  **Does not permit** a value to be "updated" because if you
 * actually _updated_ a reference associated with a key you'd _also_ be
 * changing that same reference in a lower-level cache, which would break
 * the intent of being able to commit/rollback.  Checked at runtime.  (But
 * prefer using an immutable type for the value so this problem simply
 * can't happen.)
 *
 * Type checking that the value type is consistent is the responsibility of
 * the caller.
 *
 * @param <K> key type
 */
public class UpdatableReferenceCache<K> {

    private static final String INVALID_STATE_MESSAGE = "Trying to do something in an invalid state";

    protected final Map<K, Object> original = new HashMap<>(); // "missing" denoted by null values here
    protected final Map<K, Object> current = new HashMap<>(); // "deleted" denoted by null values here
    protected final UpdatableReferenceCacheLineState<K> cacheLineStateIdentification =
            new UpdatableReferenceCacheLineState<>();

    /**
     * Create an `UpdatableReferenceCache` for holding the cached value of some type. */
    UpdatableReferenceCache() {}

    /**
     * Get from the cache
     */
    public Entry get(@NonNull final K key) {
        return getCacheLineState(key);
    }

    /**
     * Fill cache with a read from a lower level - used only in response to NOT_YET_FETCHED.
     */
    public void fill(@NonNull final K key, final Object value) {
        switch (getCacheLineState(key).state()) {
            case NOT_YET_FETCHED -> original.put(key, value);
            case MISSING, PRESENT -> throw new UpdatableCacheUsageException("Trying to override a lower-level entry");
            case UPDATED, DELETED -> throw new UpdatableCacheUsageException("Trying to override an updated entry");
            case INVALID -> throw new IllegalStateException(INVALID_STATE_MESSAGE);
        }
    }

    /**
     * Put a new k/v or update an existing k/v, but _disallows_ attempt to _update_
     * an existing k/v with _the same value_ - because that would indicate that you
     * updated the reference value breaking the commit/rollback ability of this
     * (component of a) stacked cache.
     */
    @SuppressWarnings({"fallthrough", "java:S1301"
    }) // "replace this `switch` by an `if` to improve readability" - no: wrong advice here
    public void update(@NonNull final K key, @NonNull final Object value) {
        final var currentState = getCacheLineState(key);
        switch (currentState.state()) {
            case PRESENT:
                if (currentState.value() == value) {
                    throw new UpdatableCacheUsageException("Trying to update a value with exact same reference - "
                            + "probably indicates an error where the value was modified");
                }
            // fallthrough
            case NOT_YET_FETCHED, MISSING, UPDATED, DELETED:
                current.put(key, value);
                break;
            case INVALID:
                throw new IllegalStateException(INVALID_STATE_MESSAGE);
        }
    }

    /**
     * Delete from the cache - meaning that this key is considered _deleted_, not
     * a cache miss.
     */
    public void delete(@NonNull final K key) {
        switch (getCacheLineState(key).state()) {
            case PRESENT -> current.put(key, null);
            case UPDATED -> current.remove(key);
            case NOT_YET_FETCHED ->
                throw new UpdatableCacheUsageException("Trying to delete a value that hasn't been fetched");
            case MISSING, DELETED ->
                throw new UpdatableCacheUsageException("Trying to delete a missing/already deleted value");
            case INVALID -> throw new IllegalStateException(INVALID_STATE_MESSAGE);
        }
    }

    /**
     * This is the implementation of `commit`: merge another cache's updated
     * entries over ours.  Don't need to merge the originals because the intent of
     * this class is to support a stacked cache and the lower layers of the stack
     * (such as this one, being coalesced _into_) already have those entries.
     */
    public void coalesceFrom(@NonNull final UpdatableReferenceCache<K> source) {
        current.putAll(source.current);
    }

    @NonNull
    protected Entry getCacheLineState(@NonNull final K key) {
        return cacheLineStateIdentification.get(original, current, key);
    }

    public static class UpdatableCacheUsageException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 6722692540537834117L;

        UpdatableCacheUsageException(@NonNull final String message) {
            super(message);
        }
    }
}
