// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3;

import jakarta.inject.Named;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Named("web3Properties")
@Data
@ConfigurationProperties(prefix = "hedera.mirror.web3")
@Validated
public class Web3Properties {
    @Positive
    private int maxPayloadLogSize = 300;

    @DurationMin(seconds = 1L)
    private Duration transactionTimeout = Duration.ofSeconds(10L);
}
