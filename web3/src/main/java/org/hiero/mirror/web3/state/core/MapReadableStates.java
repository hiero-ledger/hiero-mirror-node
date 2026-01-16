// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

@SuppressWarnings("unchecked")
public class MapReadableStates extends AbstractMapReadableState {

    public MapReadableStates(@NonNull final Map<Integer, ?> states) {
        super(states);
    }

    @NonNull
    @Override
    public <K, V> ReadableKVState<K, V> get(int stateId) {
        final var state = states.get(Objects.requireNonNull(stateId));
        if (state == null) {
            throw new IllegalArgumentException("Unknown k/v state id: " + stateId);
        }
        if (!(state instanceof ReadableKVState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableKVState: " + stateId);
        }

        return (ReadableKVState<K, V>) state;
    }

    @NonNull
    @Override
    public <T> ReadableSingletonState<T> getSingleton(int stateId) {
        final var state = states.get(Objects.requireNonNull(stateId));
        if (state == null) {
            throw new IllegalArgumentException("Unknown singleton state id: " + stateId);
        }

        if (!(state instanceof ReadableSingletonState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableSingletonState: " + stateId);
        }

        return (ReadableSingletonState<T>) state;
    }

    @NonNull
    @Override
    public <E> ReadableQueueState<E> getQueue(int stateId) {
        final var state = states.get(Objects.requireNonNull(stateId));
        if (state == null) {
            throw new IllegalArgumentException("Unknown queue state id: " + stateId);
        }

        if (!(state instanceof ReadableQueueState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableQueueState: " + stateId);
        }

        return (ReadableQueueState<E>) state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MapReadableStates that = (MapReadableStates) o;
        return Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(states);
    }
}
