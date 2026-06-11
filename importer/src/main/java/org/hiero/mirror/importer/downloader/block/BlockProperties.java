// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.hiero.mirror.importer.ImporterProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("blockProperties")
@ConfigurationProperties("hiero.mirror.importer.block")
@Data
@Validated
public final class BlockProperties {

    private final ImporterProperties importerProperties;

    private boolean autoDiscoveryEnabled = true;

    private String bucketName;

    private boolean enabled = false;

    @NotNull
    private Duration frequency = Duration.ofMillis(100L);

    private Path initialLedgerIdPublication;

    @NotNull
    @Valid
    private List<BlockNodeProperties> nodes = Collections.emptyList();

    private boolean persistBytes = false;

    @NotNull
    private BlockSourceType sourceType = BlockSourceType.AUTO;

    @NotNull
    @Valid
    private StreamProperties stream = new StreamProperties();

    private boolean writeFiles = false;

    public String getBucketName() {
        return StringUtils.isNotBlank(bucketName)
                ? bucketName
                : ImporterProperties.HederaNetwork.getBlockStreamBucketName(importerProperties.getNetwork());
    }

    @PostConstruct
    void validateBlockNodeProperties() {
        if (nodes.isEmpty()) {
            return;
        }

        final var badNodeIndices = new ArrayList<Integer>();
        for (int i = 0; i < nodes.size(); i++) {
            final var blockNodeProperties = nodes.get(i);
            boolean hasStatusApi = false;
            boolean hasSubscribeStreamApi = false;
            for (final var endpoint : blockNodeProperties.getEndpoints()) {
                hasStatusApi |= endpoint.getApis().contains(BlockNodeProperties.Api.STATUS);
                hasSubscribeStreamApi |= endpoint.getApis().contains(BlockNodeProperties.Api.SUBSCRIBE_STREAM);
                if (hasStatusApi && hasSubscribeStreamApi) {
                    break;
                }
            }

            if (!hasStatusApi || !hasSubscribeStreamApi) {
                badNodeIndices.add(i + 1);
            }
        }

        if (!badNodeIndices.isEmpty()) {
            throw new IllegalStateException(
                    "Block nodes (%s) are missing required Status and / or Subscribe Stream APIs"
                            .formatted(StringUtils.join(badNodeIndices, ",")));
        }
    }
}
