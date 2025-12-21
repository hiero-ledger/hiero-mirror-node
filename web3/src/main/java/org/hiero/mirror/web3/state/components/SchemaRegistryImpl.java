// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
@CustomLog
public class SchemaRegistryImpl implements SchemaRegistry<SemanticVersion> {

    /**
     * The name of the service using this registry.
     */
    private final String serviceName;

    private final StateRegistry stateRegistry;

    /**
     * The ordered set of all schemas registered by the service
     */
    @Getter
    private final SortedSet<Schema<SemanticVersion>> schemas = new TreeSet<>();

    @Override
    public SchemaRegistry<SemanticVersion> register(@NonNull Schema<SemanticVersion> schema) {
        schemas.remove(schema);
        schemas.add(schema);
        return this;
    }

    @SuppressWarnings("java:S107")
    public void migrate(
            @NonNull final String serviceName,
            @NonNull final MirrorNodeState state,
            @NonNull final Configuration appConfig) {
        if (schemas.isEmpty()) {
            return;
        }

        for (final var schema : schemas) {
            state.getReadableStates(serviceName);
            applyStateDefinitions(serviceName, schema, appConfig, state);
        }
    }

    private void applyStateDefinitions(
            @NonNull final String serviceName,
            @NonNull final Schema<SemanticVersion> schema,
            @NonNull final Configuration configuration,
            @NonNull final MirrorNodeState state) {
        final Map<Integer, Object> stateDataSources = new HashMap<>();
        final var statesToRemove = schema.statesToRemove();
        schema.statesToCreate(configuration).stream()
                .filter(def -> !statesToRemove.contains(def.stateId()))
                .forEach(def -> stateDataSources.put(def.stateId(), stateRegistry.lookup(serviceName, def)));
        state.addService(serviceName, stateDataSources);
    }
}
