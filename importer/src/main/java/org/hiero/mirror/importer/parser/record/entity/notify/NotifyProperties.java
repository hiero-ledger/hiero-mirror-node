// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.notify;

import lombok.Data;
import org.hiero.mirror.importer.parser.record.entity.BatchPublisherProperties;
import org.hiero.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConditionOnEntityRecordParser
@ConfigurationProperties("hiero.mirror.importer.parser.record.entity.notify")
@Validated
public class NotifyProperties implements BatchPublisherProperties {

    private boolean enabled = false;

    private int maxJsonPayloadSize = 8000;
}
