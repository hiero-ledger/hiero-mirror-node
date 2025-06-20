// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import jakarta.persistence.EntityManager;
import org.hiero.mirror.common.aspect.RepositoryUsageTrackerAspect;
import org.hiero.mirror.common.filter.ApiTrackingFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class TableUsageReportConfiguration {

    @Bean
    public RepositoryUsageTrackerAspect repositoryUsageTrackerAspect(final EntityManager entityManager) {
        return new RepositoryUsageTrackerAspect(entityManager);
    }

    @Bean
    public ApiTrackingFilter apiTrackingFilter() {
        return new ApiTrackingFilter();
    }
}
