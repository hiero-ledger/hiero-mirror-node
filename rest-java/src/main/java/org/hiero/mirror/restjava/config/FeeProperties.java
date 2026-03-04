// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.rest-java.fee")
public class FeeProperties {

    @NotBlank
    private String cacheSpec = "expireAfterWrite=10m";

    @NotNull
    private Map<String, String> intrinsicProperties = new HashMap<>(Map.of("fees.simpleFeesEnabled", "true"));
}
