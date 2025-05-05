// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.retriever;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.grpc.retriever")
public class RetrieverProperties {

    private boolean enabled = true;

    @Min(32)
    private int maxPageSize = 1000;

    @NotNull
    private Duration pollingFrequency = Duration.ofSeconds(2L);

    @Min(1)
    private int threadMultiplier = 4;

    @NotNull
    private Duration timeout = Duration.ofSeconds(60L);

    @NotNull
    @Valid
    private UnthrottledProperties unthrottled = new UnthrottledProperties();

    @Data
    @Validated
    public static class UnthrottledProperties {

        @Min(1000)
        private int maxPageSize = 5000;

        @Min(4)
        private long maxPolls = 12;

        @DurationMin(millis = 10)
        @NotNull
        private Duration pollingFrequency = Duration.ofMillis(20);
    }
}
