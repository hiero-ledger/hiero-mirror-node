// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.properties;

import static com.hedera.hapi.util.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static org.hiero.base.utility.CommonUtils.unhex;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_30;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_34;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_38;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_46;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_50;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.EVM_VERSION_0_51;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.config.VersionedConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.RandomUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor(onConstructor_ = {@Autowired})
@Setter
@Validated
@ConfigurationProperties(prefix = "hiero.mirror.web3.evm")
public class MirrorNodeEvmProperties implements EvmProperties {

    public static final String ALLOW_LONG_ZERO_ADDRESSES = "HIERO_MIRROR_WEB3_MODULARIZED_ALLOWLONGZEROADDRESS";

    private static final NavigableMap<Long, SemanticVersion> DEFAULT_EVM_VERSION_MAP =
            ImmutableSortedMap.of(0L, EVM_VERSION);

    @Getter
    private final CommonProperties commonProperties;

    private final SystemEntity systemEntity;

    @Value("${" + ALLOW_LONG_ZERO_ADDRESSES + ":true}")
    private boolean allowLongZeroAddresses = true;

    @Getter
    private boolean allowTreasuryToOwnNfts = true;

    @NotNull
    private Set<EntityType> autoRenewTargetTypes = new HashSet<>();

    @Getter
    @Positive
    private double estimateGasIterationThresholdPercent = 0.10d;

    private boolean directTokenCall = true;

    private boolean dynamicEvmVersion = true;

    @Min(1)
    private long exchangeRateGasReq = 100;

    private SemanticVersion evmVersion = EVM_VERSION;

    private NavigableMap<Long, SemanticVersion> evmVersions = new TreeMap<>();

    @Getter
    @NotNull
    private EvmSpecVersion evmSpecVersion = EvmSpecVersion.CANCUN;

    @Getter
    @NotNull
    @DurationMin(seconds = 1)
    private Duration expirationCacheTime = Duration.ofMinutes(10L);

    private String fundingAccount;

    @Getter
    private long htsDefaultGasCost = 10000;

    @Getter
    private boolean limitTokenAssociations = false;

    @Getter
    @Min(1)
    private long maxAutoRenewDuration = 8000001L;

    @Getter
    @Min(1)
    private int maxBatchSizeBurn = 10;

    @Getter
    @Min(1)
    private int maxBatchSizeMint = 10;

    @Getter
    @Min(1)
    private int maxBatchSizeWipe = 10;

    private int maxCustomFeesAllowed = 10;

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
    @Min(1)
    private int maxMemoUtf8Bytes = 100;

    @Getter
    private int maxNftMetadataBytes = 100;

    @Getter
    @Min(1)
    private int maxTokenNameUtf8Bytes = 100;

    @Getter
    @Min(1)
    private int maxTokensPerAccount = 1000;

    @Getter
    @Min(1)
    private int maxTokenSymbolUtf8Bytes = 100;

    @Getter
    @Min(1)
    private long minAutoRenewDuration = 2592000L;

    @Getter
    @NotNull
    private HederaNetwork network = HederaNetwork.TESTNET;

    // Contains the user defined properties to pass to the consensus node library
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
    @Min(1)
    private int feesTokenTransferUsageMultiplier = 380;

    @Getter
    private boolean modularizedServices = true;

    @Getter
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double modularizedTrafficPercent = 1.0;

    @Getter
    private long entityNumBuffer = 1000L;

    @Getter
    private long minimumAccountBalance = 100_000_000_000_000_000L;

    @Getter
    private boolean overridePayerBalanceValidation = true;

    public boolean shouldAutoRenewAccounts() {
        return autoRenewTargetTypes.contains(EntityType.ACCOUNT);
    }

    public boolean shouldAutoRenewContracts() {
        return autoRenewTargetTypes.contains(EntityType.CONTRACT);
    }

    public boolean shouldAutoRenewSomeEntityType() {
        return !autoRenewTargetTypes.isEmpty();
    }

    @Override
    public boolean isRedirectTokenCallsEnabled() {
        return directTokenCall;
    }

    @Override
    public boolean isLazyCreationEnabled() {
        return true;
    }

    @Override
    public boolean isCreate2Enabled() {
        return true;
    }

    @Override
    public boolean allowCallsToNonContractAccounts() {
        return SEMANTIC_VERSION_COMPARATOR.compare(getSemanticEvmVersion(), EVM_VERSION_0_46) >= 0;
    }

    @Override
    public Set<Address> grandfatherContracts() {
        return Set.of();
    }

    @Override
    public boolean callsToNonExistingEntitiesEnabled(Address target) {
        return !(SEMANTIC_VERSION_COMPARATOR.compare(getSemanticEvmVersion(), EVM_VERSION_0_46) < 0
                || !allowCallsToNonContractAccounts()
                || grandfatherContracts().contains(target));
    }

    @Override
    public boolean dynamicEvmVersion() {
        return dynamicEvmVersion;
    }

    @Override
    public Bytes32 chainIdBytes32() {
        return network.getChainId();
    }

    @Override
    public String evmVersion() {
        var context = ContractCallContext.get();
        if (context.useHistorical()) {
            return getEvmVersionForBlock(context.getRecordFile().getIndex()).toString();
        }
        return evmVersion.toString();
    }

    public SemanticVersion getSemanticEvmVersion() {
        var context = ContractCallContext.get();
        if (context.useHistorical()) {
            return getEvmVersionForBlock(context.getRecordFile().getIndex());
        }
        return evmVersion;
    }

    @Override
    public Address fundingAccountAddress() {
        if (fundingAccount == null) {
            fundingAccount = toAddress(systemEntity.feeCollectorAccount()).toHexString();
        }
        return Address.fromHexString(fundingAccount);
    }

    @Override
    public int maxGasRefundPercentage() {
        return maxGasRefundPercentage;
    }

    public int maxCustomFeesAllowed() {
        return maxCustomFeesAllowed;
    }

    public long exchangeRateGasReq() {
        return exchangeRateGasReq;
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

    public int feesTokenTransferUsageMultiplier() {
        return feesTokenTransferUsageMultiplier;
    }

    private Map<String, String> buildTransactionProperties() {
        var props = new HashMap<String, String>();
        props.put("contracts.chainId", chainIdBytes32().toBigInteger().toString());
        props.put("contracts.evm.version", "v" + evmVersion.major() + "." + evmVersion.minor());
        props.put("contracts.maxRefundPercentOfGasLimit", String.valueOf(maxGasRefundPercentage()));
        props.put("contracts.sidecars", "");
        props.put("contracts.systemContract.scheduleService.scheduleCall.enabled", "true");
        props.put("contracts.throttle.throttleByOpsDuration", "false");
        props.put("contracts.throttle.throttleByGas", "false");
        props.put("executor.disableThrottles", "true");
        props.put("hedera.realm", String.valueOf(commonProperties.getRealm()));
        props.put("hedera.shard", String.valueOf(commonProperties.getShard()));
        props.put("ledger.id", Bytes.wrap(getNetwork().getLedgerId()).toHexString());
        props.put("nodes.gossipFqdnRestricted", "false");
        props.put("tss.hintsEnabled", "false");
        props.put("tss.historyEnabled", "false");
        props.putAll(properties); // Allow user defined properties to override the defaults
        return Collections.unmodifiableMap(props);
    }

    /**
     * Used to determine whether a transaction should go through the txn execution service based on
     * modularizedTrafficPercent property in the config/application.yml.
     *
     * @return true if the random value between 0 and 1 is less than modularizedTrafficPercent
     */
    public boolean directTrafficThroughTransactionExecutionService() {
        return isModularizedServices()
                && RandomUtils.secure().randomDouble(0.0d, 1.0d) < getModularizedTrafficPercent();
    }

    @PostConstruct
    public void init() {
        System.setProperty(ALLOW_LONG_ZERO_ADDRESSES, Boolean.toString(allowLongZeroAddresses));
    }

    @Getter
    @RequiredArgsConstructor
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

            return Collections.unmodifiableNavigableMap(evmVersionsMap);
        }
    }
}
