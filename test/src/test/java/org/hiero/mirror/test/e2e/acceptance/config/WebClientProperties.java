// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "hiero.mirror.test.acceptance.webclient")
@Data
@RequiredArgsConstructor
@Validated
public class WebClientProperties {
    @NotNull
    @DurationMin(seconds = 5L)
    @DurationMax(seconds = 60L)
    private Duration connectionTimeout = Duration.ofSeconds(10L);

    @NotNull
    @DurationMin(seconds = 5L)
    @DurationMax(seconds = 60L)
    private Duration readTimeout = Duration.ofSeconds(10L);

    private boolean wiretap = false;

    @NotNull
    @DurationMin(seconds = 5L)
    @DurationMax(seconds = 60L)
    private Duration writeTimeout = Duration.ofSeconds(10L);
}
