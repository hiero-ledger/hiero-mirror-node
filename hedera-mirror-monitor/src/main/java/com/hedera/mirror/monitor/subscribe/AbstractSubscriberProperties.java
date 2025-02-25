// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.monitor.subscribe;

import com.hedera.mirror.monitor.ScenarioProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public abstract class AbstractSubscriberProperties extends ScenarioProperties {

    @Min(1)
    @Max(1024)
    protected int subscribers = 1;
}
