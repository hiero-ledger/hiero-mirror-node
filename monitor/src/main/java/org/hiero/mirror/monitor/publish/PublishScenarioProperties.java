// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.publish;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.monitor.ScenarioProperties;
import org.hiero.mirror.monitor.publish.transaction.TransactionType;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class PublishScenarioProperties extends ScenarioProperties {

    private boolean logResponse = false;

    // Maximum length of the transaction memo string
    @Min(13) // 13 is the length of the memo timestamp
    @Max(100)
    private int maxMemoLength = 100;

    @NotNull
    private Map<String, String> properties = new LinkedHashMap<>();

    @Min(0)
    @Max(1)
    private double receiptPercent = 0.0;

    @Min(0)
    @Max(1)
    private double recordPercent = 0.0;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration timeout = Duration.ofSeconds(13);

    @Min(0)
    private double tps = 1.0;

    @NotNull
    private TransactionType type;

    public PublishScenarioProperties() {
        retry.setMaxAttempts(1L);
    }

    @Override
    public long getLimit() {
        return limit > 0 ? limit : Long.MAX_VALUE;
    }
}
