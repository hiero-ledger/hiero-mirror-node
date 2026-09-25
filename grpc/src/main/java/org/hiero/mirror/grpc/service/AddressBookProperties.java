// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.grpc.addressbook")
public class AddressBookProperties {

    @DurationMin(millis = 500L)
    @NotNull
    private Duration cacheExpiry = Duration.ofSeconds(2);

    @Min(0)
    private long cacheSize = 50L;

    @Max(16)
    @Min(1)
    private int maxConcurrentPerConnection = 2;

    @Max(10_000)
    @Min(1)
    private int maxLimit = 1_000;

    @DurationMin(minutes = 1L)
    @NotNull
    private Duration nodeStakeCacheExpiry = Duration.ofHours(24);

    @Min(0)
    private long nodeStakeCacheSize = 5L;

    @DurationMin(millis = 100L)
    @NotNull
    private Duration pageDelay = Duration.ofMillis(250L);

    @Min(1)
    private int pageSize = 10;

    @Max(10_000)
    @Min(1)
    private int schedulerQueueSize = 32;

    @Max(64)
    @Min(1)
    private int schedulerSize = 4;

    @DurationMin(millis = 50L)
    @NotNull
    private Duration timeout = Duration.ofMinutes(2L);
}
