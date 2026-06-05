// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties(prefix = "hiero.mirror.web3")
@Validated
public class Web3Properties {

    @DurationMin(millis = 10L)
    private Duration contractCallRetryDelay = Duration.ofMillis(200L);

    private boolean contractCallStorageRetryEnabled = true;

    @Positive
    private int maxPayloadLogSize = 300;

    @DurationMin(seconds = 1L)
    private Duration requestTimeout = Duration.ofSeconds(10L);
}
