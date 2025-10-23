// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static com.hedera.node.app.state.merkle.SchemaApplicationType.MIGRATION;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.RESTART;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.STATE_DEFINITIONS;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.spi.FilteredReadableStates;
import com.swirlds.state.spi.FilteredWritableStates;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.state.core.MapWritableStates;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class SchemaRegistryImpl implements SchemaRegistry {

    private final SchemaApplications schemaApplications;
    private final StateRegistry stateRegistry;

    /**
     * The ordered set of all schemas registered by the service
     */
    @Getter
    private final SortedSet<Schema> schemas = new TreeSet<>();

    @Override
    public SchemaRegistry register(@NonNull Schema schema) {
        schemas.remove(schema);
        schemas.add(schema);
        return this;
    }

    @SuppressWarnings("java:S107")
    public void migrate(
            @NonNull final String serviceName,
            @NonNull final MirrorNodeState state,
            @Nullable final SemanticVersion previousVersion,
            @NonNull final Configuration appConfig,
            @NonNull final Configuration platformConfig,
            @NonNull final Map<String, Object> sharedValues,
            @NonNull final StartupNetworks startupNetworks) {
        if (schemas.isEmpty()) {
            return;
        }

        // For each schema, create the underlying raw data sources (maps, or lists) and the writable states that
        // will wrap them. Then call the schema's migrate method to populate those states, and commit each of them
        // to the underlying data sources. At that point, we have properly migrated the state.
        final var latestVersion = schemas.getLast().getVersion();

        for (final var schema : schemas) {
            final var applications =
                    schemaApplications.computeApplications(previousVersion, latestVersion, schema, appConfig);
            final var readableStates = state.getReadableStates(serviceName);
            final var previousStates = new FilteredReadableStates(readableStates, readableStates.stateKeys());
            final WritableStates writableStates;
            final WritableStates newStates;
            if (applications.contains(STATE_DEFINITIONS)) {
                final var redefinedWritableStates = applyStateDefinitions(serviceName, schema, appConfig, state);
                writableStates = redefinedWritableStates.beforeStates();
                newStates = redefinedWritableStates.afterStates();
            } else {
                newStates = writableStates = state.getWritableStates(serviceName);
            }
            final var context = newMigrationContext(
                    previousVersion,
                    previousStates,
                    newStates,
                    appConfig,
                    platformConfig,
                    sharedValues,
                    startupNetworks);
            if (applications.contains(MIGRATION)) {
                schema.migrate(context);
            }
            if (applications.contains(RESTART)) {
                schema.restart(context);
            }
            if (writableStates instanceof MapWritableStates mws) {
                mws.commit();
            }

            // And finally we can remove any states we need to remove
            schema.statesToRemove().forEach(stateKey -> state.removeServiceState(serviceName, stateKey));
        }
    }

    @SuppressWarnings("java:S107")
    public MigrationContext newMigrationContext(
            @Nullable final SemanticVersion previousVersion,
            @NonNull final ReadableStates previousStates,
            @NonNull final WritableStates writableStates,
            @NonNull final Configuration appConfig,
            @NonNull final Configuration platformConfig,
            @NonNull final Map<String, Object> sharedValues,
            @NonNull final StartupNetworks startupNetworks) {
        return new MigrationContext() {
            @Override
            public void copyAndReleaseOnDiskState(String stateKey) {
                // No-op
            }

            @Override
            public long roundNumber() {
                return 0;
            }

            @NonNull
            @Override
            public StartupNetworks startupNetworks() {
                return startupNetworks;
            }

            @Override
            public SemanticVersion previousVersion() {
                return previousVersion;
            }

            @NonNull
            @Override
            public ReadableStates previousStates() {
                return previousStates;
            }

            @NonNull
            @Override
            public WritableStates newStates() {
                return writableStates;
            }

            @NonNull
            @Override
            public Configuration appConfig() {
                return appConfig;
            }

            @NonNull
            @Override
            public Configuration platformConfig() {
                return platformConfig;
            }

            @Override
            public Map<String, Object> sharedValues() {
                return sharedValues;
            }

            @Override
            public boolean isGenesis() {
                return MigrationContext.super.isGenesis();
            }
        };
    }

    private RedefinedWritableStates applyStateDefinitions(
            @NonNull final String serviceName,
            @NonNull final Schema schema,
            @NonNull final Configuration configuration,
            @NonNull final MirrorNodeState state) {
        final Map<String, Object> stateDataSources = new HashMap<>();
        schema.statesToCreate(configuration)
                .forEach(def -> stateDataSources.put(def.stateKey(), stateRegistry.lookup(serviceName, def)));

        state.addService(serviceName, stateDataSources);

        final var statesToRemove = schema.statesToRemove();
        final var writableStates = state.getWritableStates(serviceName);
        final var remainingStates = new HashSet<>(writableStates.stateKeys());
        remainingStates.removeAll(statesToRemove);
        final var newStates = new FilteredWritableStates(writableStates, remainingStates);
        return new RedefinedWritableStates(writableStates, newStates);
    }

    /**
     * Encapsulates the writable states before and after applying a schema's state definitions.
     *
     * @param beforeStates the writable states before applying the schema's state definitions
     * @param afterStates  the writable states after applying the schema's state definitions
     */
    private record RedefinedWritableStates(WritableStates beforeStates, WritableStates afterStates) {}
}
