// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.config;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.state.CacheManager;
import com.hedera.mirror.web3.state.components.NoOpMetrics;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.ids.AppEntityIdFactory;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
import com.swirlds.state.lifecycle.EntityIdFactory;
import lombok.RequiredArgsConstructor;
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

    @Bean
    public CacheManager cacheManager() {
        return new CacheManager();
    }
}
