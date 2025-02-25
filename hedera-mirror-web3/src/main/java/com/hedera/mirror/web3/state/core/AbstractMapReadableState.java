// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableStates;
import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

abstract class AbstractMapReadableState implements ReadableStates {

    protected final Map<String, ?> states;

    protected AbstractMapReadableState(@Nonnull final Map<String, ?> states) {
        this.states = Objects.requireNonNull(states);
    }

    @Override
    public boolean contains(@Nonnull String stateKey) {
        return states.containsKey(stateKey);
    }

    @Nonnull
    @Override
    public Set<String> stateKeys() {
        return Collections.unmodifiableSet(states.keySet());
    }
}
