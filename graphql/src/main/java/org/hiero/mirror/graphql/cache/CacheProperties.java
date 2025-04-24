// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.cache;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hedera.mirror.graphql.cache")
@Data
@Validated
public class CacheProperties {
    @NotBlank
    private String query = "expireAfterWrite=1h,maximumSize=1000,recordStats";
}
