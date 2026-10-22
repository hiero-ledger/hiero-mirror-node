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
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties(prefix = "hiero.mirror.web3")
@Validated
public class Web3Properties {

    @NotNull
    private Map<ApiEndpointName, @Valid ApiProperties> api = new HashMap<>();

    private static final ApiProperties API_PROPERTIES = new ApiProperties();

    private boolean enableBlockOverrides = false;

    private boolean enableStateOverrides = false;

    @NotNull
    @DurationMin(seconds = 1L)
    private Duration exchangeRateRefreshInterval = Duration.ofSeconds(5L);

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
            final var properties = api.get(endpoint);
            if (properties != null
                    && properties.getResponse() != null
                    && properties.getResponse().getTimeout() != null) {
                return properties.getResponse().getTimeout();
            }
        }
        return requestTimeout;
    }

    public @NonNull ApiProperties getApi(@NonNull ApiEndpointName name) {
        return api.getOrDefault(name, API_PROPERTIES);
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
