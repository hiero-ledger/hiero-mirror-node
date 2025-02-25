// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.grpc.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class NettyProperties {

    @Min(1)
    private int executorCoreThreadCount = 10;

    @Max(10000)
    private int executorMaxThreadCount = 1000;

    @DurationMin(minutes = 1L)
    @NotNull
    private Duration maxConnectionIdle = Duration.ofMinutes(10L);

    @Min(1)
    private int maxConcurrentCallsPerConnection = 5;

    @Min(8) // 1 kb
    private int maxInboundMessageSize = 1024;

    @Min(8) // 2 kb
    private int maxInboundMetadataSize = 2048;

    @DurationMin(minutes = 0L)
    @DurationMax(minutes = 5L)
    @NotNull
    private Duration threadKeepAliveTime = Duration.ofMinutes(1L);
}
