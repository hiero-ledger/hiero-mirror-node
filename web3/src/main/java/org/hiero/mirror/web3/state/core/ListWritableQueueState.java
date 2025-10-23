// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.WritableQueueStateBase;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import org.jspecify.annotations.NonNull;

public class ListWritableQueueState<E> extends WritableQueueStateBase<E> {
    /** Represents the backing storage for this state */
    private final Queue<E> backingStore;

    /**
     * Create an instance using the given Queue as the backing store. This is useful when you want to
     * pre-populate the queue, or if you want to use Mockito to mock it or cause it to throw
     * exceptions when certain keys are accessed, etc.
     *
     * @param serviceName The service name for this state
     * @param stateKey The state key for this state
     * @param backingStore The backing store to use
     */
    public ListWritableQueueState(
            @NonNull final String serviceName, @NonNull final String stateKey, @NonNull final Queue<E> backingStore) {
        super(serviceName, stateKey);
        this.backingStore = Objects.requireNonNull(backingStore);
    }

    @Override
    protected void addToDataSource(@NonNull E element) {
        backingStore.add(element);
    }

    @Override
    protected void removeFromDataSource() {
        backingStore.remove();
    }

    @NonNull
    @Override
    protected Iterator<E> iterateOnDataSource() {
        return backingStore.iterator();
    }
}
