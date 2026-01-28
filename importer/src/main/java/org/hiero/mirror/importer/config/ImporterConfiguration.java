// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.db.DBProperties;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.hiero.mirror.importer.downloader.record.RecordDownloaderProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableResilientMethods
@EntityScan("org.hiero.mirror.importer.repository.upsert")
@CustomLog
@RequiredArgsConstructor
@AutoConfigureBefore(FlywayAutoConfiguration.class) // Since this configuration creates FlywayConfigurationCustomizer
class ImporterConfiguration {

    private final BlockProperties blockProperties;
    private final ImporterProperties importerProperties;
    private final RecordDownloaderProperties recordDownloaderProperties;

    @Bean(defaultCandidate = false)
    @FlywayDataSource
    DataSource flywayDataSource(DBProperties dbProperties, HikariConfig hikariConfig) {
        var flywayHikariConfig = new HikariConfig();
        hikariConfig.copyStateTo(flywayHikariConfig);

        flywayHikariConfig.setIdleTimeout(60000);
        flywayHikariConfig.setMinimumIdle(0);
        flywayHikariConfig.setMaximumPoolSize(10);
        flywayHikariConfig.setPassword(dbProperties.getOwnerPassword());
        flywayHikariConfig.setPoolName(hikariConfig.getPoolName() + "_flyway");
        flywayHikariConfig.setUsername(dbProperties.getOwner());
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

    @PostConstruct
    void init() {
        if (blockProperties.isEnabled() && recordDownloaderProperties.isEnabled()) {
            throw new IllegalStateException("Cannot enable both block source and record downloader");
        }
    }

    @Configuration
    @ConditionalOnProperty(
            prefix = "spring.task.scheduling",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @EnableScheduling
    protected static class SchedulingConfiguration {}
}
