// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties(prefix = "hiero.mirror.web3")
@Validated
public class Web3Properties {

    @NotNull
    private Map<ApiEndpointName, @Valid ApiProperties> api = new HashMap<>();

    private boolean enableStateOverrides = false;

    @Positive
    private int maxPayloadLogSize = 300;

    @DurationMin(seconds = 1L)
    private Duration requestTimeout = Duration.ofSeconds(4L);

    /**
     * Returns the request timeout for the given API endpoint, falling back to {@link #requestTimeout} when the endpoint
     * has no configured override.
     */
    public Duration getRequestTimeout(ApiEndpointName endpoint) {
        var timeout = getRequestProperties(endpoint).getTimeout();
        return timeout != null ? timeout : requestTimeout;
    }

    /**
     * Returns the configured response headers for the given API endpoint, or an empty map when none are configured.
     */
    public Map<String, String> getResponseHeaders(ApiEndpointName endpoint) {
        return getRequestProperties(endpoint).getHeaders();
    }

    private ApiProperties.RequestProperties getRequestProperties(ApiEndpointName endpoint) {
        if (endpoint != null) {
            var properties = api.get(endpoint);
            if (properties != null && properties.getRequest() != null) {
                return properties.getRequest();
            }
        }
        return new ApiProperties.RequestProperties();
    }

    public enum ApiEndpointName {
        CALL("/api/v1/contracts/call"),
        OPCODES("/api/v1/contracts/results/{transactionIdOrHash}/opcodes"),
        PRESTATE("/api/v1/contracts/results/{transactionIdOrHash}/prestate");

        private static final Map<String, ApiEndpointName> BY_PATH;

        static {
            var byPath = new HashMap<String, ApiEndpointName>();
            for (var endpoint : values()) {
                byPath.put(endpoint.path, endpoint);
            }
            BY_PATH = Collections.unmodifiableMap(byPath);
        }

        private final String path;

        ApiEndpointName(String path) {
            this.path = path;
        }

        public static ApiEndpointName fromPath(String path) {
            return path == null ? null : BY_PATH.get(path);
        }

        public String getPath() {
            return path;
        }
    }
}
