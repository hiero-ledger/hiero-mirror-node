// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import org.hibernate.cfg.AvailableSettings;
import org.hiero.mirror.common.config.CommonRuntimeHints;
import org.hiero.mirror.common.converter.CustomJsonFormatMapper;
import org.hiero.mirror.common.domain.SystemEntity;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ConfigurationPropertiesScan("org.hiero.mirror")
@EnableConfigurationProperties(CommonProperties.class)
@EntityScan("org.hiero.mirror.common.domain")
@ImportRuntimeHints(CommonRuntimeHints.class)
public class CommonConfiguration {
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
}
