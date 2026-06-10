// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.staking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.importer.parser.record.entity.staking")
public class StakingProperties {

    @Min(1)
    private int chunkSize = 1000000;

    @NotNull
    private Duration chunkDelay = Duration.ofMillis(500L);
}
