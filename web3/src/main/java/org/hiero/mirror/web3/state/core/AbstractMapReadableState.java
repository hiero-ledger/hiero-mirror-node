// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.core;

import com.swirlds.state.spi.ReadableStates;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.NonNull;

abstract class AbstractMapReadableState implements ReadableStates {

    protected final Map<String, ?> states;

    protected AbstractMapReadableState(@NonNull final Map<String, ?> states) {
        this.states = Objects.requireNonNull(states);
    }

    @Override
    public boolean contains(@NonNull String stateKey) {
        return states.containsKey(stateKey);
    }

    @NonNull
    @Override
    public Set<String> stateKeys() {
        return Collections.unmodifiableSet(states.keySet());
    }
}
