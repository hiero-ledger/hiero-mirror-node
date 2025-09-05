// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.importer.downloader.block.scheduler.SchedulerType;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public final class SchedulerProperties {

    @NotNull
    @Valid
    private LatencyServiceProperties latencyService = new LatencyServiceProperties();

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
    private SchedulerType type = SchedulerType.PRIORITY;

    @Data
    @Validated
    public static class LatencyServiceProperties {

        @Min(1)
        private int backlog = 1;

        @NotNull
        private Duration frequency = Duration.ofSeconds(10);

        @DurationMin(millis = 500)
        @NotNull
        private Duration timeout = Duration.ofSeconds(5);
    }
}
