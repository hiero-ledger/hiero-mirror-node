// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service.fee;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.rest-java.fee")
public class FeeProperties {

    @NotBlank
    private String cacheSpec = "expireAfterWrite=10m,maximumSize=10,recordStats";
}
