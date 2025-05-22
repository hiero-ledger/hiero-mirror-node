// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common;

import org.hiero.mirror.common.domain.SystemEntity;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan("org.hiero.mirror")
@EnableConfigurationProperties(CommonProperties.class)
@EntityScan("org.hiero.mirror.common.domain")
public class CommonConfiguration {
    @Bean
    SystemEntity systemEntity(CommonProperties commonProperties) {
        return new SystemEntity(commonProperties);
    }
}
