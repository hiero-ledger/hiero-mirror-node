// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.sql;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties("hiero.mirror.importer.parser.record.entity.sql")
@Validated
public class SqlProperties {

    private boolean enabled = true;
}
