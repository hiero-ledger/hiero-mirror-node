// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity.notify;

import com.hedera.mirror.importer.parser.record.entity.BatchPublisherProperties;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConditionOnEntityRecordParser
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity.notify")
@Validated
public class NotifyProperties implements BatchPublisherProperties {

    private boolean enabled = false;

    private int maxJsonPayloadSize = 8000;
}
