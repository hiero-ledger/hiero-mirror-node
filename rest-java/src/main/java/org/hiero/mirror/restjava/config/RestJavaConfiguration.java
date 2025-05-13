// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.jooq.DomainRecordMapperProvider;
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
