// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.parser.record.entity.sql;

import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConditionOnEntityRecordParser
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity.sql")
@Validated
public class SqlProperties {

    private boolean enabled = true;
}
