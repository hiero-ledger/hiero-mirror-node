// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.db.disk-space")
@Data
@Validated
public class DiskSpaceProperties {

    private boolean enabled = false;

    @DurationMin(seconds = 1)
    @NotNull
    private Duration checkFrequency = Duration.ofSeconds(30);

    @PositiveOrZero
    private long maxBytes = 0;
}
