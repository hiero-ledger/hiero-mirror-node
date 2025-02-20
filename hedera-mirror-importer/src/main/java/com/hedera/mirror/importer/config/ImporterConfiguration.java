/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.config;

import com.hedera.mirror.importer.ImporterProperties;
import com.hedera.mirror.importer.leader.LeaderAspect;
import com.hedera.mirror.importer.leader.LeaderService;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EntityScan({"com.hedera.mirror.common.domain", "com.hedera.mirror.importer.repository.upsert"})
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
