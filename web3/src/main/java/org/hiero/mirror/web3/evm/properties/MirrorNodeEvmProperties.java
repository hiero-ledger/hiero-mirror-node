// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static org.hiero.base.utility.CommonUtils.unhex;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_46;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_50;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_51;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_65;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_66;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_67;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.config.VersionedConfiguration;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.web3.evm")
public class MirrorNodeEvmProperties {

    private static final NavigableMap<Long, SemanticVersion> DEFAULT_EVM_VERSION_MAP =
            ImmutableSortedMap.of(0L, EVM_VERSION);

    @Positive
    private double estimateGasIterationThresholdPercent = 0.10d;

    private SemanticVersion evmVersion = EVM_VERSION;

    private NavigableMap<Long, SemanticVersion> evmVersions = new TreeMap<>();

    @Min(21_000L)
    private long maxGasLimit = 15_000_000L;

    // maximum iteration count for estimate gas' search algorithm
    private int maxGasEstimateRetriesCount = 20;

    // used by eth_estimateGas only
    @Min(1)
    @Max(100)
    private int maxGasRefundPercentage = 100;

    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    // Contains the user defined properties to pass to the consensus node library
    @NotNull
    private Map<String, String> properties = new HashMap<>();

    // Contains the default properties merged with the user defined properties to pass to the consensus node library
    @Getter(lazy = true)
    private final Map<String, String> transactionProperties = buildTransactionProperties();

    @Getter(lazy = true)
    private final VersionedConfiguration versionedConfiguration =
            new ConfigProviderImpl(false, null, getTransactionProperties()).getConfiguration();

    private long entityNumBuffer = 1000L;

    private long minimumAccountBalance = 100_000_000_000_000_000L;

    private boolean validatePayerBalance = true;

    public SemanticVersion getSemanticEvmVersion() {
        var context = ContractCallContext.get();
        if (context.useHistorical()) {
            return getEvmVersionForBlock(context.getRecordFile().getIndex());
        }
        return evmVersion;
    }

    /**
     * Returns the most appropriate mapping of EVM versions The method operates in a hierarchical manner: 1. It
     * initially attempts to use EVM versions defined in a YAML configuration. 2. If no YAML configuration is available,
     * it defaults to using EVM versions specified in the HederaNetwork enum. 3. If no versions are defined in
     * HederaNetwork, it falls back to a default map with an entry (0L, EVM_VERSION).
     *
     * @return A NavigableMap<Long, String> representing the EVM versions. The key is the block number, and the value is
     * the EVM version.
     */
    public NavigableMap<Long, SemanticVersion> getEvmVersions() {
        if (!CollectionUtils.isEmpty(evmVersions)) {
            return evmVersions;
        }

        if (!CollectionUtils.isEmpty(network.evmVersions)) {
            return network.evmVersions;
        }

        return DEFAULT_EVM_VERSION_MAP;
    }

    /**
     * Determines the most suitable EVM version for a given block number. This method finds the highest EVM version
     * whose block number is less than or equal to the specified block number. The determination is based on the
     * available EVM versions which are fetched using the getEvmVersions() method. If no specific version matches the
     * block number, it returns a default EVM version. Note: This method relies on the hierarchical logic implemented in
     * getEvmVersions() for fetching the EVM versions.
     *
     * @param blockNumber The block number for which the EVM version needs to be determined.
     * @return The most suitable EVM version for the given block number, or a default version if no specific match is
     * found.
     */
    SemanticVersion getEvmVersionForBlock(long blockNumber) {
        Entry<Long, SemanticVersion> evmEntry = getEvmVersions().floorEntry(blockNumber);
        if (evmEntry != null) {
            return evmEntry.getValue();
        } else {
            return EVM_VERSION; // Return default version if no entry matches the block number
        }
    }

    private Map<String, String> buildTransactionProperties() {
        var props = new HashMap<String, String>();
        props.put("contracts.chainId", network.getChainId().toBigInteger().toString());
        props.put("contracts.evm.version", "v" + evmVersion.major() + "." + evmVersion.minor());
        props.put("contracts.maxRefundPercentOfGasLimit", String.valueOf(maxGasRefundPercentage));
        props.put("contracts.sidecars", "");
        props.put("contracts.throttle.throttleByOpsDuration", "false");
        props.put("contracts.throttle.throttleByGas", "false");
        props.put("contracts.systemContract.scheduleService.scheduleCall.enabled", "true");
        props.put("executor.disableThrottles", "true");
        props.put("hedera.realm", String.valueOf(CommonProperties.getInstance().getRealm()));
        props.put("hedera.shard", String.valueOf(CommonProperties.getInstance().getShard()));
        props.put("ledger.id", Bytes.wrap(getNetwork().getLedgerId()).toHexString());
        props.put("nodes.gossipFqdnRestricted", "false");
        // The following 3 properties are needed to deliberately fail conditions in upstream to avoid paying rewards to
        // multiple system accounts
        props.put("nodes.nodeRewardsEnabled", "true");
        props.put("nodes.preserveMinNodeRewardBalance", "true");
        props.put("nodes.minNodeRewardBalance", String.valueOf(Long.MAX_VALUE));

        props.put("tss.hintsEnabled", "false");
        props.put("tss.historyEnabled", "false");
        props.putAll(properties); // Allow user defined properties to override the defaults
        return Collections.unmodifiableMap(props);
    }

    @RequiredArgsConstructor
    @Getter
    public enum HederaNetwork {
        MAINNET(unhex("00"), Bytes32.fromHexString("0x0127"), mainnetEvmVersionsMap()),
        TESTNET(unhex("01"), Bytes32.fromHexString("0x0128"), Collections.emptyNavigableMap()),
        PREVIEWNET(unhex("02"), Bytes32.fromHexString("0x0129"), Collections.emptyNavigableMap()),
        OTHER(unhex("03"), Bytes32.fromHexString("0x012A"), Collections.emptyNavigableMap());

        private final byte[] ledgerId;
        private final Bytes32 chainId;
        private final NavigableMap<Long, SemanticVersion> evmVersions;

        private static NavigableMap<Long, SemanticVersion> mainnetEvmVersionsMap() {
            NavigableMap<Long, SemanticVersion> evmVersionsMap = new TreeMap<>();
            evmVersionsMap.put(0L, EVM_VERSION_0_30);
            evmVersionsMap.put(44029066L, EVM_VERSION_0_34);
            evmVersionsMap.put(49117794L, EVM_VERSION_0_38);
            evmVersionsMap.put(60258042L, EVM_VERSION_0_46);
            evmVersionsMap.put(65435845L, EVM_VERSION_0_50);
            evmVersionsMap.put(66602102L, EVM_VERSION_0_51);
            evmVersionsMap.put(85011472L, EVM_VERSION_0_65);
            evmVersionsMap.put(85659065L, EVM_VERSION_0_66);
            evmVersionsMap.put(87129575L, EVM_VERSION_0_67);
            return Collections.unmodifiableNavigableMap(evmVersionsMap);
        }
    }
}
