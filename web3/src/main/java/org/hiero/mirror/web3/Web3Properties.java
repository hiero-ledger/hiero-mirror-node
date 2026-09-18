// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
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
        if (endpoint != null) {
            var properties = api.get(endpoint);
            if (properties != null
                    && properties.getRequest() != null
                    && properties.getRequest().getTimeout() != null) {
                return properties.getRequest().getTimeout();
            }
        }
        return requestTimeout;
    }

    public enum ApiEndpointName {
        ACTIONS,
        CALL,
        OPCODES
    }

    /**
     * Whether the API identified by {@code endpoint} is enabled. Missing configuration is treated as enabled except for
     * {@link ApiEndpointName#ACTIONS}, which is disabled until explicitly turned on.
     */
    public boolean isApiEnabled(final ApiEndpointName endpoint) {
        if (endpoint == null) {
            return true;
        }
        final var properties = api.get(endpoint);
        if (properties == null) {
            return endpoint != ApiEndpointName.ACTIONS;
        }
        return properties.isEnabled();
    }
}
