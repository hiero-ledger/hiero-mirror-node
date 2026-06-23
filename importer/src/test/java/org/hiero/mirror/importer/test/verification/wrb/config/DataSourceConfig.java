// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb.config;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@ConditionalOnProperty(name = "wrb.test.enabled", havingValue = "true")
@Configuration(proxyBeanMethods = false)
public final class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties recordStreamProps() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("hiero.mirror.importer.test.wrb.datasource")
    public DataSourceProperties wrbProps() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("recordStreamProps") final DataSourceProperties recordStreamProps,
            @Qualifier("wrbProps") final DataSourceProperties wrbProps) {
        final var primary = recordStreamProps.initializeDataSourceBuilder().build();
        final var secondary = wrbProps.initializeDataSourceBuilder().build();

        final var routing = new RoutingDataSource();
        routing.setTargetDataSources(Map.of(
                DataSourceContextHolder.RECORDSTREAM, primary,
                DataSourceContextHolder.WRB, secondary));
        routing.setDefaultTargetDataSource(primary);
        return routing;
    }
}
