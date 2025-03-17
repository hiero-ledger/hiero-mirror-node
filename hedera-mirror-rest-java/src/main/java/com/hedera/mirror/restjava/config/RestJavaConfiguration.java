// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.restjava.config;

import com.hedera.mirror.restjava.jooq.DomainRecordMapperProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
class RestJavaConfiguration {

    @Bean
    public DefaultConfigurationCustomizer configurationCustomizer(
            DomainRecordMapperProvider domainRecordMapperProvider) {
        return c -> c.set(domainRecordMapperProvider).settings().withRenderSchema(false);
    }
}
