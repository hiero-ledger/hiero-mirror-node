// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static com.hedera.node.app.state.merkle.SchemaApplicationType.STATE_DEFINITIONS;
import static com.hedera.node.app.state.merkle.VersionUtils.alreadyIncludesStateDefs;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.lifecycle.StateMetadata;
import com.swirlds.state.spi.FilteredReadableStates;
import com.swirlds.state.spi.FilteredWritableStates;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.state.keyvalue.StateRegistry;

@RequiredArgsConstructor
@CustomLog
public class SchemaRegistryImpl implements SchemaRegistry {

    /**
     * The name of the service using this registry.
     */
    private final String serviceName;

    private final SchemaApplications schemaApplications;
    private final StateRegistry stateRegistry;

    /**
     * The ordered set of all schemas registered by the service
     */
    @Getter
    private final SortedSet<Schema> schemas = new TreeSet<>();

    @Override
    public SchemaRegistry register(@Nonnull Schema schema) {
        schemas.remove(schema);
        schemas.add(schema);
        return this;
    }

    @SuppressWarnings("java:S107")
    public void migrate(
            @Nonnull final String serviceName,
            @Nonnull final MirrorNodeState state,
            @Nullable final SemanticVersion previousVersion,
            @Nonnull final Configuration appConfig,
            @Nonnull final Configuration platformConfig,
            @Nonnull final Map<String, Object> sharedValues,
            @Nonnull final StartupNetworks startupNetworks) {
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
            final var previousStates = new FilteredReadableStates(readableStates, readableStates.stateIds());
            final WritableStates writableStates;
            final WritableStates newStates;
            if (applications.contains(STATE_DEFINITIONS)) {
                final var schemasAlreadyInState = schemas.tailSet(schema).stream()
                        .filter(s -> s != schema
                                && previousVersion != null
                                && alreadyIncludesStateDefs(previousVersion, s.getVersion()))
                        .toList();
                final var redefinedWritableStates =
                        applyStateDefinitions(schema, schemasAlreadyInState, appConfig, state);
                writableStates = redefinedWritableStates.beforeStates();
                newStates = redefinedWritableStates.afterStates();
            } else {
                newStates = writableStates = state.getWritableStates(serviceName);
            }
            //            final var context = newMigrationContext(
            //                    previousVersion,
            //                    previousStates,
            //                    newStates,
            //                    appConfig,
            //                    platformConfig,
            //                    sharedValues,
            //                    startupNetworks);
            //            if (applications.contains(MIGRATION)) {
            //                schema.migrate(context);
            //            }
            //            if (applications.contains(RESTART)) {
            //                schema.restart(context);
            //            }
            //            if (writableStates instanceof MapWritableStates mws) {
            ////                mws.commit();
            //            }

            // And finally we can remove any states we need to remove
            //            schema.statesToRemove().forEach(stateKey -> state.removeServiceState(serviceName, stateKey));
        }
    }

    @SuppressWarnings("java:S107")
    public MigrationContext newMigrationContext(
            @Nullable final SemanticVersion previousVersion,
            @Nonnull final ReadableStates previousStates,
            @Nonnull final WritableStates writableStates,
            @Nonnull final Configuration appConfig,
            @Nonnull final Configuration platformConfig,
            @Nonnull final Map<String, Object> sharedValues,
            @Nonnull final StartupNetworks startupNetworks) {
        return new MigrationContext() {
            @Override
            public void copyAndReleaseOnDiskState(int stateKey) {
                // No-op
            }

            @Override
            public long roundNumber() {
                return 0;
            }

            @Nonnull
            @Override
            public StartupNetworks startupNetworks() {
                return startupNetworks;
            }

            @Override
            public SemanticVersion previousVersion() {
                return previousVersion;
            }

            @Nonnull
            @Override
            public ReadableStates previousStates() {
                return previousStates;
            }

            @Nonnull
            @Override
            public WritableStates newStates() {
                return writableStates;
            }

            @Nonnull
            @Override
            public Configuration appConfig() {
                return appConfig;
            }

            @Nonnull
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
            @Nonnull final Schema schema,
            @Nonnull final List<Schema> schemasAlreadyInState,
            @Nonnull final Configuration configuration,
            @Nonnull final MirrorNodeState state) {

        final Map<Integer, Object> stateDataSources = new HashMap<>();
        schema.statesToCreate(configuration)
                .forEach(def -> stateDataSources.put(def.stateId(), stateRegistry.lookup(serviceName, def)));
        //        schema.statesToCreate(configuration)
        //////                        .forEach(def -> stateDataSources.put(def.stateKey(),
        // stateRegistry.lookup(serviceName, def)));
        //                .forEach(def -> stateDataSources.put(def.stateId(), new HashMap<>()));

        state.addService(serviceName, stateDataSources);

        // Create the new states (based on the schema) which, thanks to the above, does not
        // expand the set of states that the migration code will see
        schema.statesToCreate(configuration).stream()
                .sorted(Comparator.comparing(StateDefinition::stateKey))
                .forEach(def -> {
                    final var stateKey = def.stateKey();
                    if (schemasAlreadyInState.stream()
                            .anyMatch(s -> s.statesToRemove().contains(stateKey))) {
                        log.info("  Skipping {} as it is removed by a later schema", stateKey);
                        return;
                    }
                    log.info("  Ensuring {} has state {}", serviceName, stateKey);
                    final var md = new StateMetadata<>(serviceName, schema, def);
                    //                    state.initializeState(md);
                });

        //                state.addService(serviceName, stateDataSources);

        // Create the "before" and "after" writable states (we won't commit anything
        // from these states until we have completed migration for this schema)
        //        final var statesToRemove = schema.statesToRemove();
        final var writableStates = state.getWritableStates(serviceName);
        final var remainingStates = new HashSet<>(writableStates.stateIds());
        //        remainingStates.removeAll(statesToRemove);
        //        log.info("  Removing states {} from service {}", statesToRemove, serviceName);
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
