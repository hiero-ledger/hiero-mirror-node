// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import static org.hiero.mirror.test.e2e.acceptance.config.RestProperties.URL_PREFIX;

import jakarta.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hiero.mirror.test.acceptance.rest-java")
@Data
@Named
@RequiredArgsConstructor
@Validated
public class RestJavaProperties {

    private String baseUrl;

    private boolean enabled = false;

    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.endsWith(URL_PREFIX)) {
            return baseUrl + URL_PREFIX;
        }
        return baseUrl;
    }
}
