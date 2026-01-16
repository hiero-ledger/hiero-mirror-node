// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
public abstract class ScenarioProperties {

    @NotNull
    @DurationMin(seconds = 30)
    protected Duration duration = Duration.ofNanos(Long.MAX_VALUE);

    protected boolean enabled = true;

    @Min(0)
    protected long limit = 0; // 0 for unlimited

    protected String name;

    @NotNull
    protected RetryProperties retry = new RetryProperties();

    @Data
    @Validated
    public static class RetryProperties {

        @Min(0)
        private long maxAttempts = 16L;

        @NotNull
        @DurationMin(millis = 500L)
        private Duration maxBackoff = Duration.ofSeconds(1L);

        @NotNull
        @DurationMin(millis = 100L)
        private Duration minBackoff = Duration.ofMillis(500L);
    }
}
