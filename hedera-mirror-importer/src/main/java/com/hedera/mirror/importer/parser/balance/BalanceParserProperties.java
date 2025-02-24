// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.balance;

import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.importer.parser.AbstractParserProperties;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("balanceParserProperties")
@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser.balance")
public class BalanceParserProperties extends AbstractParserProperties {

    @Min(1)
    private int batchSize = 200_000;

    @Min(1)
    private int fileBufferSize = 200_000;

    public BalanceParserProperties() {
        frequency = Duration.ofSeconds(1L);
        batch.setQueueCapacity(1);
        batch.setMaxItems(batchSize * 5L);
        retry.setMaxAttempts(3);
        transactionTimeout = Duration.ofMinutes(5L);
    }

    @Override
    public StreamType getStreamType() {
        return StreamType.BALANCE;
    }
}
