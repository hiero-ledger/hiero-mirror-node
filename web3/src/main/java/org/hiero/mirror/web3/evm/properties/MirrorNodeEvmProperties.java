// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static org.hiero.base.utility.CommonUtils.unhex;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_50;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_51;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.config.VersionedConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.util.Version;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor(onConstructor_ = {@Autowired})
@Setter
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.web3.evm")
public class MirrorNodeEvmProperties {

    public static final String ALLOW_LONG_ZERO_ADDRESSES = "HIERO_MIRROR_WEB3_EVM_ALLOWLONGZEROADDRESSES";

    private static final List<SemanticVersion> DEFAULT_EVM_VERSION_SET =
            List.of(EVM_VERSION_0_30, EVM_VERSION_0_34, EVM_VERSION_0_38, EVM_VERSION_0_50, EVM_VERSION_0_51);

    @Getter
    private final CommonProperties commonProperties;

    private final SystemEntity systemEntity;

    @Value("${" + ALLOW_LONG_ZERO_ADDRESSES + ":false}")
    private boolean allowLongZeroAddresses = false;

    @Getter
    @Positive
    private double estimateGasIterationThresholdPercent = 0.10d;

    @Getter
    private SemanticVersion evmVersion = EVM_VERSION;

    private List<SemanticVersion> evmVersions = new ArrayList<>();

    @Getter
    @NotNull
    @DurationMin(seconds = 1)
    private Duration expirationCacheTime = Duration.ofMinutes(10L);

    @Getter
    @Min(21_000L)
    private long maxGasLimit = 15_000_000L;

    // maximum iteration count for estimate gas' search algorithm
    @Getter
    private int maxGasEstimateRetriesCount = 20;

    // used by eth_estimateGas only
    @Min(1)
    @Max(100)
    private int maxGasRefundPercentage = 100;

    @Getter
    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    //    // Contains the user defined properties to pass to the consensus node library
    @Getter
    @NotNull
    private Map<String, String> properties = new HashMap<>();

    // Contains the default properties merged with the user defined properties to pass to the consensus node library
    @Getter(lazy = true)
    private final Map<String, String> transactionProperties = buildTransactionProperties();

    @Getter(lazy = true)
    private final VersionedConfiguration versionedConfiguration =
            new ConfigProviderImpl(false, null, getTransactionProperties()).getConfiguration();

    @Getter
    private long entityNumBuffer = 1000L;

    @Getter
    private long minimumAccountBalance = 100_000_000_000_000_000L;

    @Getter
    private boolean validatePayerBalance = true;

    public Bytes32 chainIdBytes32() {
        return network.getChainId();
    }

    public SemanticVersion getSemanticEvmVersion() {
        var context = ContractCallContext.get();
        if (context.useHistorical()) {
            return getEvmVersionForBlock(context.getRecordFile().getHapiVersion());
        }
        return evmVersion;
    }

    public int maxGasRefundPercentage() {
        return maxGasRefundPercentage;
    }

    /**
     * Returns the most appropriate mapping of EVM versions The method operates in a hierarchical manner: 1. It
     * initially attempts to use EVM versions defined in a YAML configuration. 2. If no versions are defined,
     * it falls back to a default set of all supported EVM_VERSIONs
     *
     * @return A Set<SemanticVersion> representing the EVM versions supported
     */
    public List<SemanticVersion> getEvmVersions() {
        if (!CollectionUtils.isEmpty(evmVersions)) {
            return evmVersions;
        }

        return DEFAULT_EVM_VERSION_SET;
    }

    /**
     * Determines the most suitable EVM version for a given block hapiVersion. This method finds the highest EVM version
     * whose hapiVersion is less than or equal to the specified block hapiVersion. The determination is based on the
     * available EVM versions which are fetched using the getEvmVersions() method. If no specific version matches the
     * block number, it returns a default EVM version. Note: This method relies on the hierarchical logic implemented in
     * getEvmVersions() for fetching the EVM versions.
     *
     * @param hapiVersion The block hapiVersion for which the EVM version needs to be determined.
     * @return The most suitable EVM version for the given block hapiVersion, or a default version if no specific match is
     * found.
     */
    SemanticVersion getEvmVersionForBlock(Version hapiVersion) {
        if (hapiVersion == null) {
            return EVM_VERSION;
        }

        SemanticVersion highestVersion = EVM_VERSION_0_30;
        for (int i = 0; i < getEvmVersions().size(); i++) {
            final var evmVersion = getEvmVersions().get(i);
            if (hapiVersion.isGreaterThanOrEqualTo(
                    new Version(evmVersion.major(), evmVersion.minor(), evmVersion.patch()))) {
                highestVersion = evmVersion;
            }
        }
        return highestVersion;
    }

    private Map<String, String> buildTransactionProperties() {
        var props = new HashMap<String, String>();
        props.put("contracts.chainId", chainIdBytes32().toBigInteger().toString());
        props.put("contracts.evm.version", "v" + evmVersion.major() + "." + evmVersion.minor());
        props.put("contracts.maxRefundPercentOfGasLimit", String.valueOf(maxGasRefundPercentage()));
        props.put("contracts.sidecars", "");
        props.put("contracts.throttle.throttleByOpsDuration", "false");
        props.put("contracts.throttle.throttleByGas", "false");
        props.put("contracts.systemContract.scheduleService.scheduleCall.enabled", "true");
        props.put("executor.disableThrottles", "true");
        props.put("hedera.realm", String.valueOf(commonProperties.getRealm()));
        props.put("hedera.shard", String.valueOf(commonProperties.getShard()));
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

    @PostConstruct
    public void init() {
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(allowLongZeroAddresses));
    }

    @Getter
    @RequiredArgsConstructor
    public enum HederaNetwork {
        MAINNET(unhex("00"), Bytes32.fromHexString("0x0127")),
        TESTNET(unhex("01"), Bytes32.fromHexString("0x0128")),
        PREVIEWNET(unhex("02"), Bytes32.fromHexString("0x0129")),
        OTHER(unhex("03"), Bytes32.fromHexString("0x012A"));

        private final byte[] ledgerId;
        private final Bytes32 chainId;
    }
}
