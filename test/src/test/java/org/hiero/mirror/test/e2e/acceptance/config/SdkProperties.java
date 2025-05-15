// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.test.e2e.acceptance.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "hiero.mirror.test.acceptance.sdk")
@Data
@RequiredArgsConstructor
@Validated
public class SdkProperties {

    @DurationMin(seconds = 1L)
    @NotNull
    private Duration grpcDeadline = Duration.ofSeconds(10L);

    @Min(1)
    private int maxAttempts = 1000;

    @Min(1)
    private int maxNodesPerTransaction = Integer.MAX_VALUE;
}
