// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.subscribe;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.hiero.mirror.monitor.ScenarioProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public abstract class AbstractSubscriberProperties extends ScenarioProperties {

    @Min(1)
    @Max(1024)
    protected int subscribers = 1;
}
