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

    public static final String CALL = "call";
    public static final String OPCODES = "opcodes";

    @NotNull
    private Map<String, @Valid ApiProperties> api = defaultApi();

    private boolean enableStateOverrides = false;

    @Positive
    private int maxPayloadLogSize = 300;

    @DurationMin(seconds = 1L)
    private Duration requestTimeout = Duration.ofSeconds(4L);

    /**
     * Returns the request timeout for the given API endpoint, falling back to {@link #requestTimeout} when the endpoint
     * has no configured override.
     */
    public Duration getRequestTimeout(String endpoint) {
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

    private static Map<String, ApiProperties> defaultApi() {
        var opcodes = new ApiProperties();
        opcodes.getRequest().setTimeout(Duration.ofSeconds(10L));
        var api = new HashMap<String, ApiProperties>();
        api.put(OPCODES, opcodes);
        return api;
    }

    @Data
    @Validated
    public static class ApiProperties {

        @NotNull
        @Valid
        private RequestProperties request = new RequestProperties();
    }

    @Data
    @Validated
    public static class RequestProperties {

        @DurationMin(seconds = 1L)
        private Duration timeout;
    }
}
