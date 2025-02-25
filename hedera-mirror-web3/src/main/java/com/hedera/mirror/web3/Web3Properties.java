// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties(prefix = "hedera.mirror.web3")
@Validated
public class Web3Properties {
    @Positive
    private int maxPayloadLogSize = 300;
}
