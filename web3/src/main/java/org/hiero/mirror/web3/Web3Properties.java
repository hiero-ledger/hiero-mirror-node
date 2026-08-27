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

    private boolean enableStateOverrides = false;

    @Positive
    private int maxPayloadLogSize = 300;

    @Positive
    private int maxTouchedAccounts = 1000;

    @DurationMin(seconds = 1L)
    private Duration requestTimeout = Duration.ofSeconds(4L);

    public @NonNull ApiProperties getApi(@NonNull ApiEndpointName name) {
        return api.getOrDefault(name, API_PROPERTIES);
    }
}
