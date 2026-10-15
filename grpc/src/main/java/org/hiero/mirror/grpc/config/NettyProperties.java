// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class NettyProperties {

    @DurationMin(seconds = 1)
    @NotNull
    private Duration keepAliveTime = Duration.ofMinutes(5);

    @DurationMin(seconds = 1)
    @NotNull
    private Duration keepAliveTimeout = Duration.ofSeconds(20);

    @Min(1)
    private int maxConcurrentCallsPerConnection = 5;

    @DurationMin(seconds = 1)
    @NotNull
    private Duration maxConnectionAge = Duration.ofDays(500);

    @DurationMin(seconds = 0)
    @NotNull
    private Duration maxConnectionAgeGrace = Duration.ofSeconds(30);

    @DurationMin(seconds = 1)
    @NotNull
    private Duration maxConnectionIdle = Duration.ofMinutes(10);

    @DurationMin(seconds = 0)
    @NotNull
    private Duration permitKeepAliveTime = Duration.ofSeconds(90);

    private boolean permitKeepAliveWithoutCalls = false;
}
