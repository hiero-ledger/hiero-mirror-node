// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hiero.mirror.common.config.CommonRuntimeHints;
import org.hiero.mirror.common.converter.CustomJsonFormatMapper;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.util.DatabaseWaiter;
import org.hiero.mirror.common.util.SpelHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan("org.hiero.mirror")
@EnableConfigurationProperties(CommonProperties.class)
@EntityScan("org.hiero.mirror.common.domain")
@ImportRuntimeHints(CommonRuntimeHints.class)
public final class CommonConfiguration {
    @Bean
    SystemEntity systemEntity(CommonProperties commonProperties) {
        return new SystemEntity(commonProperties);
    }

    @Bean
    HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return p -> {
            // Ensure Criteria API queries use bind parameters and not literals
            p.put("hibernate.criteria.literal_handling_mode", "BIND");
            p.put(AvailableSettings.GENERATE_STATISTICS, true);
            p.put(AvailableSettings.HBM2DDL_AUTO, "none");
            p.put(AvailableSettings.JSON_FORMAT_MAPPER, new CustomJsonFormatMapper());
        };
    }

    @Bean
    DatabaseWaiter dbWaiter(CommonProperties commonProperties) {
        return new DatabaseWaiter(commonProperties);
    }

    @Bean("spelHelper")
    SpelHelper spelHelper() {
        return new SpelHelper();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariConfig hikariConfig() {
        return new HikariConfig();
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    DataSource dataSource(
            DataSourceProperties dataSourceProperties, HikariConfig hikariConfig, DatabaseWaiter databaseWaiter) {

        final var jdbcUrl = dataSourceProperties.determineUrl();
        final var username = dataSourceProperties.determineUsername();
        final var password = dataSourceProperties.determinePassword();

        databaseWaiter.waitForDatabase(jdbcUrl, username, password);

        final var config = new HikariConfig();
        hikariConfig.copyStateTo(config);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);

        return new HikariDataSource(config);
    }
}
