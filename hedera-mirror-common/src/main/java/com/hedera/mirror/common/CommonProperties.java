// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.common;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.common")
public class CommonProperties {
    @Min(0)
    private long realm = 0L;

    @Min(0)
    private long shard = 0L;
}
