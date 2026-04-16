// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.cutover;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.util.Version;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.block.cutover")
@Data
@Validated
public final class CutoverProperties {

    @Nullable
    private Boolean enabled = false;

    @NotNull
    @Valid
    private CutoverFirstStageProperties firstStage = new CutoverFirstStageProperties();

    @DurationMin(seconds = 8)
    @NotNull
    private Duration threshold = Duration.ofSeconds(16);

    @Data
    @Validated
    public static class CutoverFirstStageProperties {

        private boolean enabled;

        @NotNull
        private Version hapiVersion = new Version(0, 75, 0);

        @DurationMin(seconds = 10)
        @NotNull
        private Duration latencyCheckThreshold = Duration.ofSeconds(10);

        @DurationMin(seconds = 2)
        @NotNull
        private Duration maxLatency = Duration.ofSeconds(4);
    }
}
