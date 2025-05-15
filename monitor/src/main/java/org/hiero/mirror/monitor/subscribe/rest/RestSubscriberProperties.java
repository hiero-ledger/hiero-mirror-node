// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.monitor.subscribe.AbstractSubscriberProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class RestSubscriberProperties extends AbstractSubscriberProperties {

    @NotNull
    private Set<String> publishers = new LinkedHashSet<>();

    @Min(0)
    @Max(1)
    private double samplePercent = 1.0;

    @NotNull
    @DurationMin(millis = 500)
    private Duration timeout = Duration.ofSeconds(5);

    @Override
    public long getLimit() {
        return limit > 0 ? limit : Long.MAX_VALUE;
    }
}
