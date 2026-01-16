// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.redis;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.hiero.mirror.importer.parser.record.entity.BatchPublisherProperties;
import org.hiero.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConditionOnEntityRecordParser
@ConfigurationProperties("hiero.mirror.importer.parser.record.entity.redis")
@Validated
public class RedisProperties implements BatchPublisherProperties {

    private boolean enabled = true;

    @Min(1)
    private int queueCapacity = 8;
}
