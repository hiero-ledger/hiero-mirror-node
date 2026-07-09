// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.block.scheduler")
@Data
@Validated
public final class SchedulerProperties {

    @DurationMin(millis = 100)
    @NotNull
    private Duration maxPostProcessingLatency = Duration.ofSeconds(1);

    @DurationMin(seconds = 2)
    @NotNull
    private Duration minRescheduleInterval = Duration.ofSeconds(10);

    @DurationMin(millis = 10)
    @NotNull
    private Duration rescheduleLatencyThreshold = Duration.ofMillis(50);

    @NotNull
    private SchedulerType type = SchedulerType.PRIORITY_THEN_LATENCY;
}
