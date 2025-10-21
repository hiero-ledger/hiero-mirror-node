// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import static java.util.Objects.requireNonNull;

import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableKVStateBase;
import com.swirlds.state.spi.WritableQueueState;
import com.swirlds.state.spi.WritableQueueStateBase;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings({"rawtypes", "unchecked"})
public class MapWritableStates extends AbstractMapReadableState implements WritableStates, CommittableWritableStates {

    @Nullable
    private final Runnable onCommit;

    public MapWritableStates(@Nonnull final Map<Integer, ?> states) {
        this(states, null);
    }

    public MapWritableStates(@Nonnull final Map<Integer, ?> states, @Nullable final Runnable onCommit) {
        super(states);
        this.onCommit = onCommit;
    }

    @Nonnull
    @Override
    public <K, V> WritableKVState<K, V> get(@Nonnull int stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown k/v state key: " + stateKey);
        }
        if (!(state instanceof WritableKVState)) {
            throw new IllegalArgumentException("State is not an instance of WritableKVState: " + stateKey);
        }

        return (WritableKVState<K, V>) state;
    }

    @Nonnull
    @Override
    public <T> WritableSingletonState<T> getSingleton(@Nonnull final int stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown singleton state key: " + stateKey);
        }

        if (!(state instanceof WritableSingletonState)) {
            throw new IllegalArgumentException("State is not an instance of WritableSingletonState: " + stateKey);
        }

        return (WritableSingletonState<T>) state;
    }

    @Nonnull
    @Override
    public <E> WritableQueueState<E> getQueue(@Nonnull final int stateKey) {
        final var state = states.get(requireNonNull(stateKey));
        if (state == null) {
            throw new IllegalArgumentException("Unknown queue state key: " + stateKey);
        }

        if (!(state instanceof WritableQueueState)) {
            throw new IllegalArgumentException("State is not an instance of WritableQueueState: " + stateKey);
        }

        return (WritableQueueState<E>) state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MapWritableStates that = (MapWritableStates) o;
        return Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(states);
    }

    @Override
    public void commit() {
        states.values().forEach(state -> {
            switch (state) {
                case WritableKVStateBase kv -> kv.commit();
                case WritableSingletonStateBase singleton -> singleton.commit();
                case WritableQueueStateBase queue -> queue.commit();
                default ->
                    throw new IllegalStateException(
                            "Unknown state type " + state.getClass().getName());
            }
        });
        if (onCommit != null) {
            onCommit.run();
        }
    }
}
