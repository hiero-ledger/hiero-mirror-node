// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.monitor.health;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.monitor.health.release")
public class ReleaseHealthProperties {

    @DurationMin(seconds = 30)
    @NotNull
    private Duration cacheExpiry = Duration.ofSeconds(30);

    private boolean enabled = false;

    private boolean failWhenInactive = false;
}
