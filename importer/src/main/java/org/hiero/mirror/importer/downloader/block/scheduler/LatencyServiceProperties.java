// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.scheduler;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("latencyServiceProperties")
@ConfigurationProperties("hiero.mirror.importer.block.scheduler.latency")
@Data
@Validated
public final class LatencyServiceProperties {

    @Min(1)
    private int backlog = 1;

    @DurationMin(seconds = 2)
    @NotNull
    private Duration frequency = Duration.ofSeconds(10);

    @DurationMin(millis = 500)
    @NotNull
    private Duration timeout = Duration.ofSeconds(5);
}
