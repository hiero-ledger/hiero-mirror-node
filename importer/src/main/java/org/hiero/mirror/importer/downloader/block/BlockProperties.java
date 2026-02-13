// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import lombok.Data;
import org.apache.logging.log4j.util.Strings;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.hiero.mirror.importer.ImporterProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("blockProperties")
@ConfigurationProperties("hiero.mirror.importer.block")
@Data
@Validated
public class BlockProperties {

    private final ImporterProperties importerProperties;

    private String bucketName;

    private Boolean cutover;

    @DurationMin(seconds = 8)
    @NotNull
    private Duration cutoverThreshold = Duration.ofSeconds(8);

    private boolean enabled = false;

    @NotNull
    private Duration frequency = Duration.ofMillis(100L);

    @NotNull
    @Valid
    private Collection<BlockNodeProperties> nodes = Collections.emptyList();

    private boolean persistBytes = false;

    @NotNull
    private BlockSourceType sourceType = BlockSourceType.AUTO;

    @NotNull
    @Valid
    private StreamProperties stream = new StreamProperties();

    private boolean writeFiles = false;

    public String getBucketName() {
        return Strings.isNotBlank(bucketName)
                ? bucketName
                : ImporterProperties.HederaNetwork.getBlockStreamBucketName(importerProperties.getNetwork());
    }
}
