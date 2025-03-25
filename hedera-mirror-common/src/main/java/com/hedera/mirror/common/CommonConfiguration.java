// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common;

import com.hedera.mirror.common.domain.SystemEntities;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan(basePackages = "com.hedera.mirror")
@EnableConfigurationProperties(CommonProperties.class)
@EntityScan("com.hedera.mirror.common.domain")
public class CommonConfiguration {
    @Bean
    SystemEntities systemEntities(CommonProperties commonProperties) {
        return new SystemEntities(commonProperties);
    }
}
