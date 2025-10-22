// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableSingletonState;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class MapReadableStates extends AbstractMapReadableState {

    public MapReadableStates(@Nonnull final Map<Integer, ?> states) {
        super(states);
    }

    @Nonnull
    @Override
    public <K, V> ReadableKVState<K, V> get(@Nonnull int stateId) {
        final var state = states.get(Objects.requireNonNull(stateId));
        if (state == null) {
            throw new IllegalArgumentException("Unknown k/v state key: " + stateId);
        }
        if (!(state instanceof ReadableKVState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableKVState: " + stateId);
        }

        return (ReadableKVState<K, V>) state;
    }

    @Nonnull
    @Override
    public <T> ReadableSingletonState<T> getSingleton(@Nonnull int stateKey) {
        final var state = states.get(Objects.requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown singleton state key: " + stateKey);
        }

        if (!(state instanceof ReadableSingletonState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableSingletonState: " + stateKey);
        }

        return (ReadableSingletonState<T>) state;
    }

    @Nonnull
    @Override
    public <E> ReadableQueueState<E> getQueue(@Nonnull int stateKey) {
        final var state = states.get(Objects.requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown queue state key: " + stateKey);
        }

        if (!(state instanceof ReadableQueueState)) {
            throw new IllegalArgumentException("State is not an instance of ReadableQueueState: " + stateKey);
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
