// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.config;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.hedera.node.app.service.entityid.EntityIdFactory;
import com.hedera.node.app.service.entityid.impl.AppEntityIdFactory;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.state.components.NoOpMetrics;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class ModularizedConfiguration {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Bean
    public StoreMetricsServiceImpl storeMetricsService() {
        return new StoreMetricsServiceImpl(new NoOpMetrics());
    }

    @Bean
    public ConfigProviderImpl configProvider() {
        return new ConfigProviderImpl(false, null, mirrorNodeEvmProperties.getProperties());
    }

    @Bean
    public EntityIdFactory entityIdFactory() {
        return new AppEntityIdFactory(mirrorNodeEvmProperties.getVersionedConfiguration());
    }
}
