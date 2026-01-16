// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public abstract class AbstractParserProperties implements ParserProperties {

    @NotNull
    @Valid
    protected BatchProperties batch = new BatchProperties();

    protected boolean enabled = true;

    @DurationMin(millis = 10L)
    @NotNull
    protected Duration frequency = Duration.ofMillis(20L);

    @DurationMin(seconds = 5)
    @NotNull
    protected Duration processingTimeout = Duration.ofSeconds(10L);

    @NotNull
    @Valid
    protected RetryProperties retry = new RetryProperties();

    @DurationMin(seconds = 1)
    @DurationUnit(ChronoUnit.SECONDS)
    @NotNull
    protected Duration transactionTimeout = Duration.ofSeconds(120);

    @Data
    @Validated
    public static class BatchProperties {

        @NotNull
        @DurationMin(millis = 100L)
        private Duration flushInterval = Duration.ofSeconds(2L);

        @Min(1)
        private int maxFiles = 1;

        @Min(1)
        private long maxItems = 60_000L;

        @Min(1)
        private int queueCapacity = 10;

        @NotNull
        @DurationMin(millis = 100L)
        private Duration window = Duration.ofMinutes(5L);
    }

    @Data
    @Validated
    public static class RetryProperties {

        @Min(0)
        private int maxAttempts = Integer.MAX_VALUE;

        @NotNull
        @DurationMin(millis = 500L)
        private Duration maxBackoff = Duration.ofSeconds(30L);

        @NotNull
        @DurationMin(millis = 100L)
        private Duration minBackoff = Duration.ofMillis(500L);

        @Min(1)
        private int multiplier = 2;
    }
}
