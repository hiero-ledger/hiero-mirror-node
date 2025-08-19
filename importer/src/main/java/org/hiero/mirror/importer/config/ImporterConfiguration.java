// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EntityScan("org.hiero.mirror.importer.repository.upsert")
@CustomLog
@RequiredArgsConstructor
@AutoConfigureBefore(FlywayAutoConfiguration.class) // Since this configuration creates FlywayConfigurationCustomizer
class ImporterConfiguration {

    private final ImporterProperties importerProperties;

    @Bean(defaultCandidate = false)
    @FlywayDataSource
    DataSource flywayDataSource(
            JdbcConnectionDetails connectionDetails, DBProperties dbProperties, HikariConfig hikariConfig) {
        var flywayHikariConfig = new HikariConfig();
        hikariConfig.copyStateTo(flywayHikariConfig);

        flywayHikariConfig.setJdbcUrl(connectionDetails.getJdbcUrl());
        flywayHikariConfig.setUsername(dbProperties.getOwner());
        flywayHikariConfig.setPassword(dbProperties.getOwnerPassword());
        flywayHikariConfig.setMinimumIdle(0);
        flywayHikariConfig.setIdleTimeout(60000);
        flywayHikariConfig.setMaximumPoolSize(10);
        flywayHikariConfig.setPoolName(hikariConfig.getPoolName() + "_flyway");
        return new HikariDataSource(flywayHikariConfig);
    }

    @Bean
    FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> {
            Long timestamp = importerProperties.getTopicRunningHashV2AddedTimestamp();
            if (timestamp == null) {
                if (ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(importerProperties.getNetwork())) {
                    timestamp = 1592499600000000000L;
                } else {
                    timestamp = 1588706343553042000L;
                }
            }
            configuration.getPlaceholders().put("topicRunningHashV2AddedTimestamp", timestamp.toString());
        };
    }

    @Configuration
    @ConditionalOnProperty(prefix = "spring.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableRetry
    protected static class RetryConfiguration {}

    @Configuration
    @ConditionalOnProperty(
            prefix = "spring.task.scheduling",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @EnableScheduling
    protected static class SchedulingConfiguration {}
}
