/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.state.components;

import static com.hedera.node.app.state.merkle.SchemaApplicationType.MIGRATION;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.RESTART;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.STATE_DEFINITIONS;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.mirror.web3.state.core.MapWritableStates;
import com.hedera.mirror.web3.state.singleton.DefaultSingleton;
import com.hedera.mirror.web3.state.singleton.SingletonState;
import com.hedera.node.app.state.merkle.SchemaApplications;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.spi.FilteredReadableStates;
import com.swirlds.state.spi.FilteredWritableStates;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SchemaRegistryImpl implements SchemaRegistry {

    public static final SemanticVersion CURRENT_VERSION = new SemanticVersion(0, 47, 0, "SNAPSHOT", "");

    private final Collection<SingletonState<?>> singletons;
    private final SchemaApplications schemaApplications;

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

    @SuppressWarnings("rawtypes")
    public void migrate(
            @Nonnull final String serviceName,
            @Nonnull final MirrorNodeState state,
            @Nonnull final NetworkInfo networkInfo,
            @Nonnull final StartupNetworks startupNetworks) {
        final var config = ConfigurationBuilder.create().build();
        migrate(
                serviceName,
                state,
                CURRENT_VERSION,
                networkInfo,
                config,
                config,
                new HashMap<>(),
                new AtomicLong(),
                startupNetworks);
    }

    @SuppressWarnings("java:S107")
    public void migrate(
            @Nonnull final String serviceName,
            @Nonnull final MirrorNodeState state,
            @Nullable final SemanticVersion previousVersion,
            @Nonnull final NetworkInfo networkInfo,
            @Nonnull final Configuration appConfig,
            @Nonnull final Configuration platformConfig,
            @Nonnull final Map<String, Object> sharedValues,
            @Nonnull final AtomicLong nextEntityNum,
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
                    networkInfo,
                    nextEntityNum,
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
            @Nonnull final ReadableStates previousStates,
            @Nonnull final WritableStates writableStates,
            @Nonnull final Configuration appConfig,
            @Nonnull final Configuration platformConfig,
            @Nonnull final NetworkInfo networkInfo,
            @Nonnull final AtomicLong nextEntityNum,
            @Nonnull final Map<String, Object> sharedValues,
            @Nonnull final StartupNetworks startupNetworks) {
        return new MigrationContext() {
            @Override
            public void copyAndReleaseOnDiskState(String stateKey) {
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
            public long newEntityNumForAccount() {
                return nextEntityNum.getAndIncrement();
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
            public NetworkInfo genesisNetworkInfo() {
                return networkInfo;
            }

            @Override
            public Map<String, Object> sharedValues() {
                return sharedValues;
            }

            @Override
            public boolean isGenesis() {
                return MigrationContext.super.isGenesis();
            }

            @Override
            public <T extends Comparable<? super T>> boolean isUpgrade(
                    @Nonnull Function<Configuration, T> currentVersionFn,
                    @Nonnull Function<SemanticVersion, T> previousVersionFn) {
                return MigrationContext.super.isUpgrade(currentVersionFn, previousVersionFn);
            }
        };
    }

    private RedefinedWritableStates applyStateDefinitions(
            @Nonnull final String serviceName,
            @Nonnull final Schema schema,
            @Nonnull final Configuration configuration,
            @Nonnull final MirrorNodeState state) {
        final Map<String, Object> stateDataSources = new HashMap<>();
        var singletonMap = singletons.stream().collect(Collectors.toMap(SingletonState::getKey, Function.identity()));
        schema.statesToCreate(configuration).forEach(def -> {
            if (def.singleton()) {
                var singleton = singletonMap.computeIfAbsent(def.stateKey(), DefaultSingleton::new);
                stateDataSources.put(singleton.getKey(), singleton);
            } else if (def.queue()) {
                stateDataSources.put(def.stateKey(), new ConcurrentLinkedDeque<>());
            } else {
                stateDataSources.put(def.stateKey(), new ConcurrentHashMap<>());
            }
        });

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
