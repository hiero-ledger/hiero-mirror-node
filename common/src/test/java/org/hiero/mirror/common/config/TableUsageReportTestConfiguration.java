// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import jakarta.persistence.EntityManager;
import org.hiero.mirror.common.filter.ApiTrackingFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackages = "org.hiero.mirror",
        repositoryFactoryBeanClass = TrackingRepositoryFactoryBean.class)
class TableUsageReportTestConfiguration {
    @Bean
    TrackingRepositoryProxyPostProcessor trackingRepositoryProxyPostProcessor(final EntityManager entityManager) {
        return new TrackingRepositoryProxyPostProcessor(entityManager);
    }

    @Bean
    ApiTrackingFilter apiTrackingFilter() {
        return new ApiTrackingFilter();
    }
}
