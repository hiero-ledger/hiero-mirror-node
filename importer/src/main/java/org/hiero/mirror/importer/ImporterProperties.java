// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.hiero.mirror.importer.domain.StreamFileData;
import org.hiero.mirror.importer.migration.MigrationProperties;
import org.hiero.mirror.importer.util.Utility;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.util.Version;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hiero.mirror.importer")
public class ImporterProperties {

    public static final String STREAMS = "streams";
    static final String NETWORK_PREFIX_DELIMITER = "-";

    @NotNull
    private ConsensusMode consensusMode = ConsensusMode.STAKE_IN_ADDRESS_BOOK;

    @NotNull
    private Path dataPath = Paths.get(".", "data");

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    private final Path streamPath = dataPath.resolve(STREAMS);

    @PositiveOrZero
    private Long endBlockNumber;

    @NotNull
    private Instant endDate = Utility.MAX_INSTANT_LONG;

    private boolean groupByDay = true;

    private boolean importHistoricalAccountInfo = true;

    private Path initialAddressBook;

    @NotNull
    @Valid
    private Map<String, MigrationProperties> migration = new CaseInsensitiveMap<>();

    @NotBlank
    private String network = HederaNetwork.DEMO;

    private String nodePublicKey;

    private Instant startDate;

    @Min(-1)
    private Long startBlockNumber;

    private Long topicRunningHashV2AddedTimestamp;

    @NotNull
    private Version smartContractThrottlingVersion = Version.parse("0.69.0");

    public Path getArchiveDestinationFolderPath(StreamFileData streamFileData) {
        if (groupByDay) {
            return getStreamPath().resolve(streamFileData.getFilename().substring(0, 10));
        }

        return getStreamPath();
    }

    public String getNetwork() {
        return StringUtils.substringBefore(this.network, NETWORK_PREFIX_DELIMITER)
                .toLowerCase();
    }

    public String getNetworkPrefix() {
        var networkPrefix = StringUtils.substringAfter(this.network, NETWORK_PREFIX_DELIMITER);
        return StringUtils.isEmpty(networkPrefix) ? null : networkPrefix.toLowerCase();
    }

    public enum ConsensusMode {
        EQUAL, // all nodes equally weighted
        STAKE, // all nodes specify their node stake
        STAKE_IN_ADDRESS_BOOK // like STAKE, but only the nodes found in the address book are used in the calculation.
    }

    @NullMarked
    public final class HederaNetwork {
        public static final String DEMO = "demo";
        public static final String MAINNET = "mainnet";
        public static final String OTHER = "other";
        public static final String PREVIEWNET = "previewnet";
        public static final String TESTNET = "testnet";

        private static final Map<String, CloudBucket> NETWORK_DEFAULT_BUCKETS = Map.of(
                DEMO, new CloudBucket("hedera-demo-recent-block-streams", "hedera-demo-streams"),
                MAINNET, new CloudBucket("hedera-mainnet-recent-block-streams", "hedera-mainnet-streams"),
                // OTHER has no default bucket
                PREVIEWNET, new CloudBucket("hedera-previewnet-recent-block-streams", "hedera-preview-testnet-streams"),
                TESTNET, new CloudBucket("hedera-testnet-recent-block-streams", "hedera-testnet-streams-2024-02"));

        private HederaNetwork() {}

        public static String getBlockStreamBucketName(final String network) {
            return Optional.ofNullable(NETWORK_DEFAULT_BUCKETS.get(network))
                    .map(CloudBucket::blockStream)
                    .orElse("");
        }

        public static String getBucketName(final String network) {
            return Optional.ofNullable(NETWORK_DEFAULT_BUCKETS.get(network))
                    .map(CloudBucket::recordStream)
                    .orElse("");
        }

        public static boolean hasCutover(final String network) {
            return MAINNET.equals(network) || TESTNET.equals(network);
        }

        public static boolean isAllowAnonymousAccess(final String network) {
            return DEMO.equals(network);
        }

        public static boolean isResettable(final String network) {
            return !DEMO.equals(network) && !MAINNET.equals(network);
        }
    }

    private record CloudBucket(String blockStream, String recordStream) {}
}
