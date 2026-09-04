// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import org.hiero.mirror.restjava.dto.NetworkNodeDto;
import org.hiero.mirror.restjava.repository.NetworkNodeRowMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.DefaultQueryMappingConfiguration;

@Configuration
class RestJavaQueryMappingConfiguration {

    // Contributed to the common QueryMappingConfiguration bean, which adds JPA-like null-id row handling
    @Bean
    DefaultQueryMappingConfiguration restJavaQueryMappings() {
        return new DefaultQueryMappingConfiguration()
                .registerRowMapper(NetworkNodeDto.class, new NetworkNodeRowMapper());
    }
}
