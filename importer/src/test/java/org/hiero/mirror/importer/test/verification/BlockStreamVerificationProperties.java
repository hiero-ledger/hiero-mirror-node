// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.test.verification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("hiero.mirror.importer.test.blockstream")
@Data
public class BlockStreamVerificationProperties {

    private long endConsensusTimestamp = Long.MAX_VALUE;
    private long startConsensusTimestamp;
}
