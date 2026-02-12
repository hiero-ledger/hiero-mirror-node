// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.hiero.mirror.importer.ImporterProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties("hiero.mirror.importer.block.bucket")
@Data
@RequiredArgsConstructor
@Validated
public final class BlockBucketProperties {

    private final ImporterProperties importerProperties;

    private String bucketName;

    private Boolean resettable;

    public String getBucketName() {
        return Strings.isNotBlank(bucketName)
                ? bucketName
                : ImporterProperties.HederaNetwork.getBlockStreamBucketName(importerProperties.getNetwork());
    }

    boolean isResettable() {
        return resettable != null
                ? resettable
                : ImporterProperties.HederaNetwork.isResettable(importerProperties.getNetwork());
    }
}
