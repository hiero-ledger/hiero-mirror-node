// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.components;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChanges.Builder;
import com.hedera.mirror.web3.state.MirrorNodeState;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.service.PlatformStateFacade;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Named
public class ServiceMigratorImpl implements ServiceMigrator {

    @Override
    public List<Builder> doMigrations(
            @Nonnull State state,
            @Nonnull ServicesRegistry servicesRegistry,
            @Nullable SoftwareVersion previousVersion,
            @Nonnull SoftwareVersion currentVersion,
            @Nonnull Configuration appConfig,
            @Nonnull Configuration platformConfig,
            @Nullable NetworkInfo genesisNetworkInfo,
            @Nonnull Metrics metrics,
            @Nonnull StartupNetworks startupNetworks,
            @Nonnull StoreMetricsServiceImpl storeMetricsService,
            @Nonnull ConfigProviderImpl configProvider,
            @Nonnull PlatformStateFacade platformStateFacade) {
        requireNonNull(state);
        requireNonNull(servicesRegistry);
        requireNonNull(currentVersion);
        requireNonNull(appConfig);
        requireNonNull(platformConfig);
        requireNonNull(genesisNetworkInfo);
        requireNonNull(metrics);

        if (!(state instanceof MirrorNodeState mirrorNodeState)) {
            throw new IllegalArgumentException("Can only be used with MirrorNodeState instances");
        }

        if (!(servicesRegistry instanceof ServicesRegistryImpl registry)) {
            throw new IllegalArgumentException("Can only be used with ServicesRegistryImpl instances");
        }

        final AtomicLong prevEntityNum =
                new AtomicLong(appConfig.getConfigData(HederaConfig.class).firstUserEntity() - 1);
        final Map<String, Object> sharedValues = new HashMap<>();
        final var deserializedPbjVersion = Optional.ofNullable(previousVersion)
                .map(SoftwareVersion::getPbjSemanticVersion)
                .orElse(null);

        registry.registrations().stream().forEach(registration -> {
            if (!(registration.registry() instanceof SchemaRegistryImpl schemaRegistry)) {
                throw new IllegalArgumentException("Can only be used with SchemaRegistryImpl instances");
            }
            schemaRegistry.migrate(
                    registration.serviceName(),
                    mirrorNodeState,
                    deserializedPbjVersion,
                    genesisNetworkInfo,
                    appConfig,
                    platformConfig,
                    sharedValues,
                    prevEntityNum,
                    startupNetworks);
        });
        return List.of();
    }
}
