// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import java.util.Collections;
import java.util.Set;

public class TestSchemaBuilder {

    private final SemanticVersion version;
    private Set<? extends StateDefinition<?, ?>> stateDefinitions = Collections.emptySet();
    private Set<String> statesToRemove = Collections.emptySet();

    public TestSchemaBuilder(SemanticVersion version) {
        this.version = version;
    }

    public TestSchemaBuilder withStates(Set<? extends StateDefinition<?, ?>> stateDefs) {
        this.stateDefinitions = stateDefs;
        return this;
    }

    public TestSchemaBuilder withStatesToRemove(Set<String> statesToRemove) {
        this.statesToRemove = statesToRemove;
        return this;
    }

    public Schema build() {
        return new Schema(version) {
            @Override
            public Set<StateDefinition> statesToCreate() {
                @SuppressWarnings("unchecked")
                // this is needed as generics are invariant
                Set<StateDefinition> raw = (Set<StateDefinition>) (Set<?>) stateDefinitions;
                return raw;
            }

            @Override
            public Set<String> statesToRemove() {
                return statesToRemove;
            }
        };
    }
}
