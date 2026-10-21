// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.rest-java.query")
public final class QueryProperties {

    @DurationMin(nanos = 1)
    private Duration maxClprTimestampRange = Duration.ofDays(30);

    @Min(1)
    private int maxFileAttempts = 12;
}
