// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.leader.LeaderAspect;
import org.hiero.mirror.importer.leader.LeaderService;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
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

    @Bean
    @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
    @ConditionalOnProperty(value = "spring.cloud.kubernetes.leader.enabled")
    LeaderAspect leaderAspect() {
        return new LeaderAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    LeaderService leaderService() {
        return Boolean.TRUE::booleanValue; // Leader election not available outside Kubernetes
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.flyway.hikari")
    @Owner
    HikariConfig flywayHikariConfig(JdbcConnectionDetails connectionDetails) {
        var config = new HikariConfig();
        config.setJdbcUrl(connectionDetails.getJdbcUrl());
        return config;
    }

    @Bean
    FlywayConfigurationCustomizer flywayConfigurationCustomizer(
            @Owner HikariConfig hikariConfig, ApplicationContext context) {
        return configuration -> {
            Long timestamp = importerProperties.getTopicRunningHashV2AddedTimestamp();
            if (timestamp == null) {
                if (ImporterProperties.HederaNetwork.MAINNET.equalsIgnoreCase(importerProperties.getNetwork())) {
                    timestamp = 1592499600000000000L;
                } else {
                    timestamp = 1588706343553042000L;
                }
            }
            var flywayDatasource = new HikariDataSource(hikariConfig);
            var beanFactory = (DefaultListableBeanFactory) context.getAutowireCapableBeanFactory();
            beanFactory.registerDisposableBean("flywayDataSource", flywayDatasource::close);

            configuration.getPlaceholders().put("topicRunningHashV2AddedTimestamp", timestamp.toString());
            configuration.dataSource(flywayDatasource);
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
