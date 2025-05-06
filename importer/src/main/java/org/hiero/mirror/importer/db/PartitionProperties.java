// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.db;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hiero.mirror.importer.db.partition")
@Data
@Validated
public class PartitionProperties {
    @NotBlank
    private String cron = "0 0 0 * * ?";

    private boolean enabled = true;
}
