// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.utils;

import static com.hedera.hapi.util.HapiUtils.SEMANTIC_VERSION_COMPARATOR;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TestSchemaBuilder {

    private final SemanticVersion version;
    private Set<? extends StateDefinition<?, ?>> stateDefinitions = Collections.emptySet();
    private Set<Integer> statesToRemove = Collections.emptySet();

    public TestSchemaBuilder(SemanticVersion version) {
        this.version = version;
    }

    public TestSchemaBuilder withStates(Set<? extends StateDefinition<?, ?>> stateDefs) {
        this.stateDefinitions = stateDefs;
        return this;
    }

    public TestSchemaBuilder withStatesToRemove(Set<Integer> statesToRemove) {
        this.statesToRemove = statesToRemove;
        return this;
    }

    @SuppressWarnings("rawtypes")
    public Schema<SemanticVersion> build() {
        return new Schema<>(version, SEMANTIC_VERSION_COMPARATOR) {
            @Override
            public Set<StateDefinition> statesToCreate() {
                // Create a raw-typed copy compatible with Schema's signature
                Set<StateDefinition> defs = new HashSet<>();
                defs.addAll(stateDefinitions);
                return defs;
            }

            @Override
            public Set<Integer> statesToRemove() {
                return statesToRemove;
            }
        };
    }
}
