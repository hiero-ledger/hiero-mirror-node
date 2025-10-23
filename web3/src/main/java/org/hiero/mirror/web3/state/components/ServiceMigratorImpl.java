// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.components;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChanges.Builder;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.MerkleNodeState;
import com.swirlds.platform.state.service.PlatformStateFacade;
import com.swirlds.state.lifecycle.StartupNetworks;
import jakarta.inject.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Named
public class ServiceMigratorImpl implements ServiceMigrator {

    @Override
    public List<Builder> doMigrations(
            @NonNull MerkleNodeState state,
            @NonNull ServicesRegistry servicesRegistry,
            @Nullable SemanticVersion previousVersion,
            @NonNull SemanticVersion currentVersion,
            @NonNull Configuration appConfig,
            @NonNull Configuration platformConfig,
            @NonNull StartupNetworks startupNetworks,
            @NonNull StoreMetricsServiceImpl storeMetricsService,
            @NonNull ConfigProviderImpl configProvider,
            @NonNull PlatformStateFacade platformStateFacade) {
        requireNonNull(state);
        requireNonNull(servicesRegistry);
        requireNonNull(currentVersion);
        requireNonNull(appConfig);
        requireNonNull(platformConfig);

        if (!(state instanceof MirrorNodeState mirrorNodeState)) {
            throw new IllegalArgumentException("Can only be used with MirrorNodeState instances");
        }

        if (!(servicesRegistry instanceof ServicesRegistryImpl registry)) {
            throw new IllegalArgumentException("Can only be used with ServicesRegistryImpl instances");
        }

        final AtomicLong prevEntityNum =
                new AtomicLong(appConfig.getConfigData(HederaConfig.class).firstUserEntity() - 1);
        final Map<String, Object> sharedValues = new HashMap<>();

        registry.registrations().stream().forEach(registration -> {
            if (!(registration.registry() instanceof SchemaRegistryImpl schemaRegistry)) {
                throw new IllegalArgumentException("Can only be used with SchemaRegistryImpl instances");
            }
            schemaRegistry.migrate(
                    registration.serviceName(),
                    mirrorNodeState,
                    previousVersion,
                    appConfig,
                    platformConfig,
                    sharedValues,
                    startupNetworks);
        });
        return List.of();
    }
}
