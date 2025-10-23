// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.store;

import com.google.common.annotations.VisibleForTesting;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.store.accessor.DatabaseAccessor;
import org.jspecify.annotations.NonNull;

@Named
public class StackedStateFrames {

    /** All the database accessors for the value types this stacked cache can hold */
    @NonNull
    protected final List<DatabaseAccessor<Object, ?>> accessors;
    /** All the `Class`es for the value types this stacked cache can hold */
    @NonNull
    protected final Class<?>[] valueClasses;

    /** Create a `StackedStackFrames` stacked cache at base level given the database accessors for all the value
     * types this cache will hold.
     */
    public StackedStateFrames(@NonNull final List<DatabaseAccessor<Object, ?>> accessors) {
        this.accessors = accessors;
        this.valueClasses = accessors.stream()
                .map(DatabaseAccessor::getValueClass)
                .distinct()
                .toArray(Class[]::new);

        if (valueClasses.length != accessors.size()) {
            throw new IllegalArgumentException("Accessors must be for distinct types");
        }
        final var nUniqueAccessors = accessors.stream()
                .map(DatabaseAccessor::getKeyClass)
                .map(Class::getTypeName)
                .distinct()
                .count();
        if (nUniqueAccessors != 1) {
            throw new IllegalArgumentException("Key types for all accessors must be the same");
        }

        // Initial state is a R/O cache on top of the database. You should use push() to create a new R/W cache layer on
        // top of the stack,
        // so that you can commit specific changes from a nested transaction
    }

    public CachingStateFrame<Object> getInitializedStackBase(final Optional<Long> timestamp) {
        final var database = new DatabaseBackedStateFrame<>(accessors, valueClasses, timestamp);
        return new ROCachingStateFrame<>(Optional.of(database), valueClasses);
    }

    /** Return the "visible"/"effective" height of the stacked cache _only including_ those frames you've pushed on top
     * of it (after initial construction).
     */
    public int height() {
        return ContractCallContext.get().getStackHeight();
    }

    /** Return the _total_ height (aka depth) of the stacked cache _including_ the always-present stack base of a
     * RO-cache frame on top of the DB-based frame.
     */
    public int cachedFramesDepth() {
        return ContractCallContext.get().getStack().height();
    }

    /** Get the top of the stacked cache.  (Make your queries of the cache here, at the top: they'll be propagated
     * upstream as needed.)
     */
    @NonNull
    public CachingStateFrame<Object> top() {
        return ContractCallContext.get().getStack();
    }

    /** Push a new RW-frame cache on top of the stacked cache. */
    @NonNull
    public CachingStateFrame<Object> push() {
        final var contractCallContext = ContractCallContext.get();
        CachingStateFrame<Object> newFrame =
                new RWCachingStateFrame<>(Optional.of(contractCallContext.getStack()), valueClasses);
        contractCallContext.setStack(newFrame);
        return newFrame;
    }

    /** Pop a frame's cache from the top of the stacked cache. */
    public void pop() {
        ContractCallContext.get().updateStackFromUpstream();
    }

    /** Get the classes of all the value types this stacked cache can hold. */
    @NonNull
    public Class<?>[] getValueClasses() {
        return valueClasses;
    }

    /** It may happen that you want to push some special frame type on the stack, not a standard `RWCachingStateFrame`.
     * For example, a spy.  You can do that with this method, but be sure your new TOS has the current TOS as its
     * upstream.
     */
    @VisibleForTesting
    @NonNull
    CachingStateFrame<Object> push(@NonNull final CachingStateFrame<Object> frame) {
        final var contractCallContext = ContractCallContext.get();
        if (!frame.getUpstream().equals(Optional.of(contractCallContext.getStack()))) {
            throw new IllegalArgumentException("Frame argument must have current TOS as its upstream");
        }
        contractCallContext.setStack(frame);
        return frame;
    }
}
