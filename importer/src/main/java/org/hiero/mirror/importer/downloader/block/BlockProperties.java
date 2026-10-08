// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block;

import static org.hiero.mirror.importer.downloader.block.BlockNodeProperties.FULL_BLOCK_NODE_APIS;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.common.domain.transaction.BlockSourceType;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component("blockProperties")
@ConfigurationProperties("hiero.mirror.importer.block")
@Data
@Validated
public final class BlockProperties {

    // Hex encoded ledger ids by network. Populated once the ledger ids of the public networks are available.
    private static final Map<String, String> DEFAULT_LEDGER_IDS = Map.of();

    private final ImporterProperties importerProperties;

    private boolean autoDiscoveryEnabled = true;

    private String bucketName;

    private boolean enabled = false;

    @NotNull
    private Duration frequency = Duration.ofMillis(500L);

    @Pattern(regexp = "^([0-9a-fA-F]{128})?$", message = "ledgerId must be 64 bytes in hex")
    private String ledgerId;

    @NotNull
    private List<@Valid BlockNodeProperties> nodes = List.of();

    private boolean persistBytes = false;

    @NotNull
    private BlockSourceType sourceType = BlockSourceType.AUTO;

    @NotNull
    @Valid
    private StreamProperties stream = new StreamProperties();

    private boolean writeFiles = false;

    @PostConstruct
    void init() {
        StreamFileData.setMaxDecompressedBytes(stream.getMaxBlockSize().toBytes());
    }

    public String getBucketName() {
        return StringUtils.isNotBlank(bucketName)
                ? bucketName
                : ImporterProperties.HederaNetwork.getBlockStreamBucketName(importerProperties.getNetwork());
    }

    public byte @Nullable [] getLedgerId() {
        final var value =
                StringUtils.isNotBlank(ledgerId) ? ledgerId : DEFAULT_LEDGER_IDS.get(importerProperties.getNetwork());
        return value != null ? HexFormat.of().parseHex(value) : null;
    }

    @AssertTrue(message = "Each node must contain both STATUS and SUBSCRIBE_STREAM capable endpoints")
    private boolean hasValidEndpoints() {
        return nodes.stream()
                .allMatch(n -> n.getEndpoints().stream()
                        .flatMap(e -> e.getApis().stream())
                        .collect(Collectors.toSet())
                        .containsAll(FULL_BLOCK_NODE_APIS));
    }
}
