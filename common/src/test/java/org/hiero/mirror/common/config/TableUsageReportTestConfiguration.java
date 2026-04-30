// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.config;

import org.hiero.mirror.common.filter.ApiTrackingFilter;
import org.hiero.mirror.common.tableusage.TrackingRepositoryProxyPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.repository.core.support.RepositoryFactoryCustomizer;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

@TestConfiguration(proxyBeanMethods = false)
@EnableJdbcRepositories(basePackages = "org.hiero.mirror")
class TableUsageReportTestConfiguration {

    @Bean
    RepositoryFactoryCustomizer jdbcTableUsageRepositoryFactoryCustomizer(JdbcMappingContext mappingContext) {
        return (RepositoryFactorySupport factory) ->
                factory.addRepositoryProxyPostProcessor(new TrackingRepositoryProxyPostProcessor(mappingContext));
    }

    @Bean
    ApiTrackingFilter apiTrackingFilter() {
        return new ApiTrackingFilter();
    }
}
