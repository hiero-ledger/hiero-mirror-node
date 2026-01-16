// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.function.Predicate;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class NodeValidationProperties {

    private static final int TLS_PORT = 50212;

    private boolean enabled = true;

    @DurationMin(seconds = 30)
    @NotNull
    private Duration frequency = Duration.ofDays(1L);

    @Min(1)
    private int maxAttempts = 8;

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration maxBackoff = Duration.ofSeconds(2);

    @Min(1)
    private int maxEndpointsPerNode = 1;

    @Min(1)
    private int maxNodes = 30;

    @Min(1)
    private int maxThreads = 25;

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration minBackoff = Duration.ofMillis(500);

    @DurationMin(millis = 100L)
    @NotNull
    private Duration retryBackoff = Duration.ofMinutes(2L);

    // requestTimeout should be longer than the total retry time controlled by maxAttempts and backoffs
    // the default would result in a max of 11.5s without considering any network delay
    @DurationMin(millis = 500)
    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(15L);

    private boolean retrieveAddressBook = true;

    private TlsMode tls = TlsMode.BOTH;

    @Getter
    @RequiredArgsConstructor
    public enum TlsMode {
        BOTH(n -> true),
        PLAINTEXT(port -> port != TLS_PORT),
        TLS(port -> port == TLS_PORT);

        private final Predicate<Integer> predicate;
    }
}
