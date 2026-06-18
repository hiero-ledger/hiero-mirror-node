// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification.wrb;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties primaryProps() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("hiero.mirror.importer.test.wrb.datasource")
    public DataSourceProperties secondaryProps() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource routingDataSource() {
        final var primary = primaryProps().initializeDataSourceBuilder().build();
        final var secondary = secondaryProps().initializeDataSourceBuilder().build();

        var routing = new RoutingDataSource();
        routing.setTargetDataSources(Map.of(
                DataSourceContextHolder.RECORDSTREAM, primary,
                DataSourceContextHolder.WRB, secondary));
        routing.setDefaultTargetDataSource(primary);
        return routing;
    }
}
