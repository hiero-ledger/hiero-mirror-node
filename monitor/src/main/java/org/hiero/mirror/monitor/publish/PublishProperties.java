// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.monitor.publish")
public class PublishProperties {

    private boolean async = true;

    @Min(100)
    private int batchDivisor = 100;

    @Min(1)
    private int clients = 4;

    private boolean enabled = true;

    @NotNull
    private Duration nodeMaxBackoff = Duration.ofMinutes(1L);

    @NotNull
    @Valid
    private Map<String, PublishScenarioProperties> scenarios = new LinkedHashMap<>();

    @DurationMin(seconds = 1L)
    @NotNull
    private Duration statusFrequency = Duration.ofSeconds(10L);

    @Min(1)
    private int responseThreads = 40;

    @DurationMin(seconds = 0)
    @NotNull
    private Duration warmupPeriod = Duration.ofSeconds(30L);

    @PostConstruct
    void validate() {
        if (enabled && scenarios.isEmpty()) {
            throw new IllegalArgumentException("There must be at least one publish scenario");
        }

        if (scenarios.keySet().stream().anyMatch(StringUtils::isBlank)) {
            throw new IllegalArgumentException("Publish scenario name cannot be empty");
        }

        scenarios.forEach((name, property) -> property.setName(name));
    }
}
