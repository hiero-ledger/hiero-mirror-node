// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.controller;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hiero.mirror.web3.action.tracer")
@Data
@Validated
public class ActionsProperties {

    @NotNull
    @DurationMin(millis = 1)
    @DurationMax(seconds = 10)
    private Duration maxTimeout = Duration.ofSeconds(10);

    /**
     * Whether {@code /contracts/call/actions} accepts {@code debug_traceCallMany} requests: more than one bundle, or more
     * than one transaction in a bundle. When false, such requests are rejected with 501 and only a single bundle with a
     * single transaction ({@code debug_traceCall}) is accepted. Disabled by default.
     */
    private boolean traceMultipleTransactions;
}
