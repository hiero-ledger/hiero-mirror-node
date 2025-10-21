// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableStates;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

abstract class AbstractMapReadableState implements ReadableStates {

    protected final Map<Integer, ?> states;

    protected AbstractMapReadableState(@Nonnull final Map<Integer, ?> states) {
        this.states = Objects.requireNonNull(states);
    }

    @Override
    public boolean contains(@Nonnull int stateKey) {
        return states.containsKey(stateKey);
    }

    @Nonnull
    @Override
    public Set<Integer> stateIds() {
        return Collections.unmodifiableSet(states.keySet());
    }
}
