// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.monitor.health.subscriber")
public class SubscriberHealthProperties {

    /**
     * Number of consecutive non-DOWN results required to clear a DOWN status. Defaults to the GCE load
     * balancer's unhealthyThreshold so a real outage reliably produces enough consecutive DOWN polls to
     * pull the backend out of rotation, instead of one lucky successful poll masking it.
     */
    @Min(1)
    private int recoveryThreshold = 3;
}
