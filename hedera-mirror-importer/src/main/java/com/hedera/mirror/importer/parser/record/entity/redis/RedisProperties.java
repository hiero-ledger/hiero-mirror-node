// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity.redis;

import com.hedera.mirror.importer.parser.record.entity.BatchPublisherProperties;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConditionOnEntityRecordParser
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity.redis")
@Validated
public class RedisProperties implements BatchPublisherProperties {

    private boolean enabled = true;

    @Min(1)
    private int queueCapacity = 8;
}
