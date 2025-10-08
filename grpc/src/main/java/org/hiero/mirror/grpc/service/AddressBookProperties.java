// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.service;

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
    private Duration cacheExpiry = Duration.ofSeconds(60);

    @Min(0)
    private long cacheSize = 50L;

    @DurationMin(minutes = 1L)
    @NotNull
    private Duration nodeStakeCacheExpiry = Duration.ofHours(24);

    @Min(0)
    private long nodeStakeCacheSize = 5L;

    @DurationMin(millis = 500L)
    @NotNull
    private Duration nodeAccountCacheExpiry = Duration.ofSeconds(2);

    @Min(0)
    private long nodeAccountCacheSize = 50L;

    @DurationMin(millis = 100L)
    @NotNull
    private Duration maxPageDelay = Duration.ofMillis(250L);

    @DurationMin(millis = 100L)
    @NotNull
    private Duration minPageDelay = Duration.ofMillis(100L);

    @Min(1)
    private int pageSize = 10;
}
