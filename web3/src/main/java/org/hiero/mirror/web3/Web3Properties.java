// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
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

    private boolean enableStateOverrides = false;

    @NotNull
    @DurationMin(seconds = 1L)
    private Duration exchangeRateRefreshInterval = Duration.ofSeconds(5L);

    @Positive
    private int maxPayloadLogSize = 300;

    @NotNull
    @Valid
    private ResponseConfig response = new ResponseConfig();

    /*
     * Post process the configured response headers. Default headers are inherited by each configured endpoint, and
     * endpoint headers override them. Header names are treated case insensitively.
     */
    @PostConstruct
    void mergeHeaders() {
        final var defaults = response.getHeaders().getDefaults();
        for (final var apiProperties : api.values()) {
            final var headers = apiProperties.getResponse().getHeaders();
            final var merged = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            merged.putAll(defaults);
            merged.putAll(headers);
            apiProperties.getResponse().setHeaders(merged);
        }
    }

    public @NonNull ApiProperties getApi(@NonNull ApiEndpointName name) {
        return api.getOrDefault(name, API_PROPERTIES);
    }

    @Data
    @Validated
    public static class ResponseConfig {

        @NotNull
        @Valid
        private ResponseHeadersConfig headers = new ResponseHeadersConfig();
    }

    @Data
    @Validated
    public static class ResponseHeadersConfig {

        @NotNull
        private Map<String, String> defaults = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
}
