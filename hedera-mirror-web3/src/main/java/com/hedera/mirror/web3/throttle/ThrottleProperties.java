// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.throttle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.throttle")
public class ThrottleProperties {

    @Getter
    @Min(1)
    private long requestsPerSecond = 500;

    @Getter
    @Min(21_000)
    @Max(1_000_000_000)
    private long gasPerSecond = 1_000_000_000L;

    @Getter
    @Min(1)
    private int gasUnit = 1;

    @Getter
    @Min(0)
    @Max(100)
    private float gasLimitRefundPercent = 100;
}
