// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.db.disk-space")
@Data
@Validated
public class DiskSpaceProperties {

    private boolean enabled = false;

    @DurationMin(seconds = 10)
    @NotNull
    private Duration checkFrequency = Duration.ofSeconds(30);

    private List<@NotNull DataSize> maxDatabaseSizes = Collections.emptyList();

    @NotNull
    @Valid
    private Threshold threshold = new Threshold();

    @Validated
    @Data
    public static class Threshold {

        @Max(95)
        @Min(90)
        private int halt = 95;

        @Max(90)
        @Min(80)
        private int warn = 80;
    }
}
