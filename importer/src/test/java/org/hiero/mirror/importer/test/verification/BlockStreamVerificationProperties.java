// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.test.blockstream")
@Data
@Validated
public class BlockStreamVerificationProperties {

    @Max(1000)
    @Min(25)
    private int batchSize = 100;

    @Min(0)
    private long endConsensusTimestamp = Long.MAX_VALUE;

    @Min(0)
    private long startConsensusTimestamp;
}
