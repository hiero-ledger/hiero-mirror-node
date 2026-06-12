// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.hiero.mirror.importer.downloader.block.BlockNodeProperties.FULL_BLOCK_NODE_APIS;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
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
    private List<@Valid BlockNodeProperties> nodes = List.of();

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

    @AssertTrue(message = "Each node must contain both STATUS and SUBSCRIBE_STREAM capable endpoints")
    private boolean hasValidEndpoints() {
        return nodes.stream().allMatch(n -> n.getEndpoints().stream()
                .flatMap(e -> e.getApis().stream())
                .collect(Collectors.toSet())
                .containsAll(FULL_BLOCK_NODE_APIS));
    }
}
