// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
// This class can be deleted once convert to servlet + virtual threads
class RestClientConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
