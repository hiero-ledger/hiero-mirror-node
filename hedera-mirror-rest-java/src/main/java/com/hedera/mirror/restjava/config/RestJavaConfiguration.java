// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.config;

import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.restjava.common.EntityIdNumParameter;
import com.hedera.mirror.restjava.jooq.DomainRecordMapperProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
class RestJavaConfiguration {

    private final CommonProperties commonProperties;

    @PostConstruct
    void init() {
        EntityIdNumParameter.PROPERTIES.set(commonProperties);
    }

    @Bean
    public DefaultConfigurationCustomizer configurationCustomizer(
            DomainRecordMapperProvider domainRecordMapperProvider) {
        return c -> c.set(domainRecordMapperProvider).settings().withRenderSchema(false);
    }
}
