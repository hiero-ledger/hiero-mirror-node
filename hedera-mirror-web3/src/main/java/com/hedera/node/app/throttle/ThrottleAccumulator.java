// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL_LOCAL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor.ONE_TO_ONE;
import static com.hedera.node.app.service.token.AliasUtils.isAlias;
import static com.hedera.node.app.service.token.AliasUtils.isEntityNumAlias;
import static com.hedera.node.app.service.token.AliasUtils.isOfEvmAddressSize;
import static com.hedera.node.app.service.token.AliasUtils.isSerializedProtoKey;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleGroup;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// This class is a copy of the one from hedera-app and only a temporary solution.
// The difference is that it overrides some of the methods to return false,
// so that the transactions will not be throttled.
public class ThrottleAccumulator {
    private static final Logger log = LogManager.getLogger(ThrottleAccumulator.class);
    private static final Set<HederaFunctionality> GAS_THROTTLED_FUNCTIONS =
            EnumSet.of(CONTRACT_CALL_LOCAL, CONTRACT_CALL, CONTRACT_CREATE, ETHEREUM_TRANSACTION);
    private static final Set<HederaFunctionality> AUTO_CREATE_FUNCTIONS =
            EnumSet.of(CRYPTO_TRANSFER, ETHEREUM_TRANSACTION);
    private static final int UNKNOWN_NUM_IMPLICIT_CREATIONS = -1;

    private EnumMap<HederaFunctionality, ThrottleReqsManager> functionReqs = new EnumMap<>(HederaFunctionality.class);
    private boolean lastTxnWasGasThrottled;
    private GasLimitDeterministicThrottle gasThrottle;
    private List<DeterministicThrottle> activeThrottles = emptyList();

    @Nullable
    private final ThrottleMetrics throttleMetrics;

    private final CommonProperties commonProperties = CommonProperties.getInstance();

    private final Function<SemanticVersion, SoftwareVersion> softwareVersionFactory;

    private final Supplier<Configuration> configSupplier;
    private final IntSupplier capacitySplitSource;
    private final ThrottleType throttleType;
    private final Verbose verbose;

    /**
     * Whether the accumulator should log verbose definitions.
     */
    public enum Verbose {
        YES,
        NO
    }

    public ThrottleAccumulator(
            @NonNull final Supplier<Configuration> configSupplier,
            @NonNull final IntSupplier capacitySplitSource,
            @NonNull final ThrottleType throttleType,
            @NonNull Function<SemanticVersion, SoftwareVersion> softwareVersionFactory) {
        this(capacitySplitSource, configSupplier, throttleType, null, Verbose.NO, softwareVersionFactory);
    }

    public ThrottleAccumulator(
            @NonNull final IntSupplier capacitySplitSource,
            @NonNull final Supplier<Configuration> configSupplier,
            @NonNull final ThrottleType throttleType,
            @Nullable final ThrottleMetrics throttleMetrics,
            @NonNull final Verbose verbose,
            @NonNull Function<SemanticVersion, SoftwareVersion> softwareVersionFactory) {
        this.configSupplier = requireNonNull(configSupplier, "configProvider must not be null");
        this.capacitySplitSource = requireNonNull(capacitySplitSource, "capacitySplitSource must not be null");
        this.throttleType = requireNonNull(throttleType, "throttleType must not be null");
        this.verbose = requireNonNull(verbose);
        this.throttleMetrics = throttleMetrics;
        this.softwareVersionFactory = softwareVersionFactory;
    }

    // For testing purposes, in practice the gas throttle is
    // lazy-initialized based on the configuration before handling
    // any transactions
    @VisibleForTesting
    public ThrottleAccumulator(
            @NonNull final IntSupplier capacitySplitSource,
            @NonNull final Supplier<Configuration> configSupplier,
            @NonNull final ThrottleType throttleType,
            @NonNull final ThrottleMetrics throttleMetrics,
            @NonNull final GasLimitDeterministicThrottle gasThrottle,
            @NonNull Function<SemanticVersion, SoftwareVersion> softwareVersionFactory) {
        this.configSupplier = requireNonNull(configSupplier, "configProvider must not be null");
        this.capacitySplitSource = requireNonNull(capacitySplitSource, "capacitySplitSource must not be null");
        this.throttleType = requireNonNull(throttleType, "throttleType must not be null");
        this.gasThrottle = requireNonNull(gasThrottle, "gasThrottle must not be null");
        this.softwareVersionFactory = softwareVersionFactory;

        this.throttleMetrics = throttleMetrics;
        this.throttleMetrics.setupGasThrottleMetric(gasThrottle, configSupplier.get());
        this.verbose = Verbose.YES;
    }

    /**
     * Tries to claim throttle capacity for the given transaction and returns whether the transaction
     * should be throttled if there is no capacity.
     *
     * @param txnInfo the transaction to update the throttle requirements for
     * @param now the instant of time the transaction throttling should be checked for
     * @param state the current state of the node
     * @return whether the transaction should be throttled
     */
    public boolean checkAndEnforceThrottle(
            @NonNull final TransactionInfo txnInfo, @NonNull final Instant now, @NonNull final State state) {
        return false;
    }

    /**
     * Updates the throttle requirements for the given query and returns whether the query should be throttled.
     *
     * @param queryFunction the functionality of the query
     * @param now the time at which the query is being processed
     * @param query the query to update the throttle requirements for
     * @param state the current state of the node
     * @param queryPayerId the payer id of the query
     * @return whether the query should be throttled
     */
    public boolean checkAndEnforceThrottle(
            @NonNull final HederaFunctionality queryFunction,
            @NonNull final Instant now,
            @NonNull final Query query,
            @NonNull final State state,
            @Nullable final AccountID queryPayerId) {
        return false;
    }

    private int getAssociationCount(@NonNull final Query query, @NonNull final ReadableAccountStore accountStore) {
        final var accountID = query.cryptogetAccountBalanceOrThrow().accountID();
        if (accountID != null) {
            final var account = accountStore.getAccountById(accountID);
            if (account != null) {
                return account.numberAssociations();
            }
        }
        return 0;
    }

    /**
     * Updates the throttle requirements for given number of transactions of same functionality and returns whether they should be throttled.
     *
     * @param n the number of transactions to consider
     * @param function the functionality type of the transactions
     * @param consensusTime the consensus time of the transaction
     * @return whether the transaction should be throttled
     */
    public boolean shouldThrottleNOfUnscaled(
            final int n, @NonNull final HederaFunctionality function, @NonNull final Instant consensusTime) {
        return false;
    }

    /**
     * Undoes the claimed capacity for a number of transactions of the same functionality.
     *
     * @param n the number of transactions to consider
     * @param function the functionality type of the transactions
     */
    public void leakCapacityForNOfUnscaled(final int n, @NonNull final HederaFunctionality function) {}

    /**
     * Leaks the gas amount previously reserved for the given transaction.
     *
     * @param txnInfo the transaction to leak the gas for
     * @param value the amount of gas to leak
     */
    public void leakUnusedGasPreviouslyReserved(@NonNull final TransactionInfo txnInfo, final long value) {}

    /**
     * Gets the current list of active throttles.
     *
     * @return the current list of active throttles
     */
    @NonNull
    public List<DeterministicThrottle> allActiveThrottles() {
        return activeThrottles;
    }

    /**
     * Gets the current list of active throttles for the given functionality.
     *
     * @param function the functionality to get the active throttles for
     * @return the current list of active throttles for the given functionality
     */
    @NonNull
    public List<DeterministicThrottle> activeThrottlesFor(@NonNull final HederaFunctionality function) {
        final var manager = functionReqs.get(function);
        if (manager == null) {
            return emptyList();
        } else {
            return manager.managedThrottles();
        }
    }

    /**
     * Indicates whether the last transaction was throttled by gas.
     *
     * @return whether the last transaction was throttled by gas
     */
    public boolean wasLastTxnGasThrottled() {
        return lastTxnWasGasThrottled;
    }

    /**
     * Checks if the given functionality should be throttled by gas.
     *
     * @param function the functionality to check
     * @return whether the given functionality should be throttled by gas
     */
    public static boolean isGasThrottled(@NonNull final HederaFunctionality function) {
        return GAS_THROTTLED_FUNCTIONS.contains(function);
    }

    public static boolean canAutoCreate(@NonNull final HederaFunctionality function) {
        return AUTO_CREATE_FUNCTIONS.contains(function);
    }

    public static boolean canAutoAssociate(@NonNull final HederaFunctionality function) {
        return function == CRYPTO_TRANSFER;
    }

    /**
     * Updates all metrics for the active throttles and the gas throttle
     */
    public void updateAllMetrics() {
        if (throttleMetrics != null) {
            throttleMetrics.updateAllMetrics();
        }
    }

    private boolean shouldThrottleTxn(
            final boolean isScheduled,
            @NonNull final TransactionInfo txnInfo,
            @NonNull final Instant now,
            @NonNull final State state) {
        return false;
    }

    private boolean shouldThrottleScheduleCreate(
            final ThrottleReqsManager manager, final TransactionInfo txnInfo, final Instant now, final State state) {
        return false;
    }

    private static boolean throttleExempt(
            @Nullable final AccountID accountID, @NonNull final Configuration configuration) {
        final long maxThrottleExemptNum =
                configuration.getConfigData(AccountsConfig.class).lastThrottleExempt();
        if (accountID != null) {
            final var accountNum = accountID.accountNumOrElse(0L);
            return 1L <= accountNum && accountNum <= maxThrottleExemptNum;
        }
        return false;
    }

    private void reclaimLastAllowedUse() {
        activeThrottles.forEach(DeterministicThrottle::reclaimLastAllowedUse);
        gasThrottle.reclaimLastAllowedUse();
    }

    private void resetLastAllowedUse() {
        activeThrottles.forEach(DeterministicThrottle::resetLastAllowedUse);
        gasThrottle.resetLastAllowedUse();
    }

    /**
     * Returns the gas limit for a contract transaction.
     *
     * @param txnBody  the transaction body
     * @param function the functionality
     * @return the gas limit for a contract transaction
     */
    private long getGasLimitForContractTx(
            @NonNull final TransactionBody txnBody, @NonNull final HederaFunctionality function) {
        final long nominalGas =
                switch (function) {
                    case CONTRACT_CREATE -> txnBody.contractCreateInstanceOrThrow()
                            .gas();
                    case CONTRACT_CALL -> txnBody.contractCallOrThrow().gas();
                    case ETHEREUM_TRANSACTION -> Optional.of(txnBody.ethereumTransactionOrThrow()
                                    .ethereumData()
                                    .toByteArray())
                            .map(EthTxData::populateEthTxData)
                            .map(EthTxData::gasLimit)
                            .orElse(0L);
                    default -> 0L;
                };
        // Interpret negative gas as overflow
        return nominalGas < 0 ? Long.MAX_VALUE : nominalGas;
    }

    private boolean isGasExhausted(
            @NonNull final TransactionInfo txnInfo,
            @NonNull final Instant now,
            @NonNull final Configuration configuration) {
        final boolean shouldThrottleByGas =
                configuration.getConfigData(ContractsConfig.class).throttleThrottleByGas();
        return shouldThrottleByGas
                && isGasThrottled(txnInfo.functionality())
                && !gasThrottle.allow(now, getGasLimitForContractTx(txnInfo.txBody(), txnInfo.functionality()));
    }

    private boolean shouldThrottleMint(
            @NonNull final ThrottleReqsManager manager,
            @NonNull final TokenMintTransactionBody op,
            @NonNull final Instant now,
            @NonNull final Configuration configuration) {
        final int numNfts = op.metadata().size();
        if (numNfts == 0) {
            return !manager.allReqsMetAt(now);
        } else {
            final var nftsMintThrottleScaleFactor =
                    configuration.getConfigData(TokensConfig.class).nftsMintThrottleScaleFactor();
            return !manager.allReqsMetAt(now, numNfts, nftsMintThrottleScaleFactor);
        }
    }

    private boolean shouldThrottleCryptoTransfer(
            @NonNull final ThrottleReqsManager manager,
            @NonNull final Instant now,
            @NonNull final Configuration configuration,
            final int implicitCreationsCount,
            final int autoAssociationsCount) {
        final boolean isAutoCreationEnabled =
                configuration.getConfigData(AutoCreationConfig.class).enabled();
        final boolean isLazyCreationEnabled =
                configuration.getConfigData(LazyCreationConfig.class).enabled();
        final boolean unlimitedAutoAssociations =
                configuration.getConfigData(EntitiesConfig.class).unlimitedAutoAssociationsEnabled();
        if ((isAutoCreationEnabled || isLazyCreationEnabled) && implicitCreationsCount > 0) {
            return shouldThrottleBasedOnImplicitCreations(manager, implicitCreationsCount, now);
        } else if (unlimitedAutoAssociations && autoAssociationsCount > 0) {
            return shouldThrottleBasedOnAutoAssociations(manager, autoAssociationsCount, now);
        } else {
            return !manager.allReqsMetAt(now);
        }
    }

    private boolean shouldThrottleEthTxn(
            @NonNull final ThrottleReqsManager manager,
            @NonNull final Instant now,
            @NonNull final Configuration configuration,
            final int implicitCreationsCount) {
        final boolean isAutoCreationEnabled =
                configuration.getConfigData(AutoCreationConfig.class).enabled();
        final boolean isLazyCreationEnabled =
                configuration.getConfigData(LazyCreationConfig.class).enabled();
        if (isAutoCreationEnabled && isLazyCreationEnabled) {
            return shouldThrottleBasedOnImplicitCreations(manager, implicitCreationsCount, now);
        } else {
            return !manager.allReqsMetAt(now);
        }
    }

    public int getImplicitCreationsCount(
            @NonNull final TransactionBody txnBody, @NonNull final ReadableAccountStore accountStore) {
        int implicitCreationsCount = 0;
        if (txnBody.hasEthereumTransaction()) {
            final var ethTxData = populateEthTxData(
                    txnBody.ethereumTransaction().ethereumData().toByteArray());
            if (ethTxData == null) {
                return UNKNOWN_NUM_IMPLICIT_CREATIONS;
            }
            final var config = configSupplier.get().getConfigData(HederaConfig.class);
            final boolean doesNotExist = !accountStore.containsAlias(
                    commonProperties.getShard(), commonProperties.getRealm(), Bytes.wrap(ethTxData.to()));
            if (doesNotExist && ethTxData.value().compareTo(BigInteger.ZERO) > 0) {
                implicitCreationsCount++;
            }
        } else {
            final var cryptoTransferBody = txnBody.cryptoTransfer();
            if (cryptoTransferBody == null) {
                return 0;
            }

            implicitCreationsCount += hbarAdjustsImplicitCreationsCount(accountStore, cryptoTransferBody);
            implicitCreationsCount += tokenAdjustsImplicitCreationsCount(accountStore, cryptoTransferBody);
        }

        return implicitCreationsCount;
    }

    public int getAutoAssociationsCount(
            @NonNull final TransactionBody txnBody, @NonNull final ReadableTokenRelationStore relationStore) {
        int autoAssociationsCount = 0;
        final var cryptoTransferBody = txnBody.cryptoTransfer();
        if (cryptoTransferBody == null || cryptoTransferBody.tokenTransfers().isEmpty()) {
            return 0;
        }
        for (var transfer : cryptoTransferBody.tokenTransfers()) {
            final var tokenID = transfer.token();
            autoAssociationsCount += (int) transfer.transfers().stream()
                    .filter(accountAmount -> accountAmount.amount() > 0)
                    .map(AccountAmount::accountID)
                    .filter(accountID -> hasNoRelation(relationStore, accountID, tokenID))
                    .count();
            autoAssociationsCount += (int) transfer.nftTransfers().stream()
                    .map(NftTransfer::receiverAccountID)
                    .filter(receiverID -> hasNoRelation(relationStore, receiverID, tokenID))
                    .count();
        }
        return autoAssociationsCount;
    }

    private boolean hasNoRelation(
            @NonNull ReadableTokenRelationStore relationStore, @NonNull AccountID accountID, @NonNull TokenID tokenID) {
        return relationStore.get(accountID, tokenID) == null;
    }

    private int hbarAdjustsImplicitCreationsCount(
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final CryptoTransferTransactionBody cryptoTransferBody) {
        if (cryptoTransferBody.transfers() == null
                || cryptoTransferBody.transfers().accountAmounts() == null) {
            return 0;
        }

        int implicitCreationsCount = 0;
        for (var adjust : cryptoTransferBody.transfers().accountAmounts()) {
            if (referencesAliasNotInUse(adjust.accountIDOrElse(AccountID.DEFAULT), accountStore)
                    && isPlausibleAutoCreate(adjust)) {
                implicitCreationsCount++;
            }
        }

        return implicitCreationsCount;
    }

    private int tokenAdjustsImplicitCreationsCount(
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final CryptoTransferTransactionBody cryptoTransferBody) {
        if (cryptoTransferBody.tokenTransfers() == null) {
            return 0;
        }

        int implicitCreationsCount = 0;
        for (var tokenAdjust : cryptoTransferBody.tokenTransfers()) {
            for (final var adjust : tokenAdjust.transfers()) {
                if (referencesAliasNotInUse(adjust.accountID(), accountStore) && isPlausibleAutoCreate(adjust)) {
                    implicitCreationsCount++;
                }
            }

            for (final var change : tokenAdjust.nftTransfers()) {
                if (referencesAliasNotInUse(change.receiverAccountID(), accountStore)
                        && isPlausibleAutoCreate(change)) {
                    implicitCreationsCount++;
                }
            }
        }

        return implicitCreationsCount;
    }

    private boolean usesAliases(final CryptoTransferTransactionBody transferBody) {
        for (var adjust : transferBody.transfersOrElse(TransferList.DEFAULT).accountAmounts()) {
            if (isAlias(adjust.accountIDOrElse(AccountID.DEFAULT))) {
                return true;
            }
        }

        for (var tokenAdjusts : transferBody.tokenTransfers()) {
            for (var ownershipChange : tokenAdjusts.nftTransfers()) {
                if (isAlias(ownershipChange.senderAccountIDOrElse(AccountID.DEFAULT))
                        || isAlias(ownershipChange.receiverAccountIDOrElse(AccountID.DEFAULT))) {
                    return true;
                }
            }
            for (var tokenAdjust : tokenAdjusts.transfers()) {
                if (isAlias(tokenAdjust.accountIDOrElse(AccountID.DEFAULT))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean referencesAliasNotInUse(
            @NonNull final AccountID idOrAlias, @NonNull final ReadableAccountStore accountStore) {
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.aliasOrElse(Bytes.EMPTY);
            if (isOfEvmAddressSize(alias) && isEntityNumAlias(alias, idOrAlias.shardNum(), idOrAlias.realmNum())) {
                return false;
            }
            return accountStore.getAccountIDByAlias(idOrAlias.shardNum(), idOrAlias.realmNum(), alias) == null;
        }
        return false;
    }

    private boolean isPlausibleAutoCreate(@NonNull final AccountAmount adjust) {
        return isPlausibleAutoCreate(
                adjust.amount(), adjust.accountIDOrElse(AccountID.DEFAULT).aliasOrElse(Bytes.EMPTY));
    }

    private boolean isPlausibleAutoCreate(@NonNull final NftTransfer change) {
        return isPlausibleAutoCreate(
                change.serialNumber(),
                change.receiverAccountIDOrElse(AccountID.DEFAULT).aliasOrElse(Bytes.EMPTY));
    }

    private boolean isPlausibleAutoCreate(final long assetChange, @NonNull final Bytes alias) {
        if (assetChange > 0) {
            if (isSerializedProtoKey(alias)) {
                return true;
            } else {
                return isOfEvmAddressSize(alias);
            }
        }

        return false;
    }

    private boolean shouldThrottleBasedOnImplicitCreations(
            @NonNull final ThrottleReqsManager manager, final int implicitCreationsCount, @NonNull final Instant now) {
        return (implicitCreationsCount == 0)
                ? !manager.allReqsMetAt(now)
                : shouldThrottleImplicitCreations(implicitCreationsCount, now);
    }

    private boolean shouldThrottleBasedOnAutoAssociations(
            @NonNull final ThrottleReqsManager manager, final int autoAssociations, @NonNull final Instant now) {
        return (autoAssociations == 0)
                ? !manager.allReqsMetAt(now)
                : shouldThrottleAutoAssociations(autoAssociations, now);
    }

    private boolean shouldThrottleImplicitCreations(final int n, @NonNull final Instant now) {
        final var manager = functionReqs.get(CRYPTO_CREATE);
        return manager == null || !manager.allReqsMetAt(now, n, ONE_TO_ONE);
    }

    private boolean shouldThrottleAutoAssociations(final int n, @NonNull final Instant now) {
        final var manager = functionReqs.get(TOKEN_ASSOCIATE_TO_ACCOUNT);
        return manager == null || !manager.allReqsMetAt(now, n, ONE_TO_ONE);
    }

    /**
     * Rebuilds the throttle requirements based on the given throttle definitions.
     *
     * @param defs the throttle definitions to rebuild the throttle requirements based on
     */
    public void rebuildFor(@NonNull final ThrottleDefinitions defs) {
        // No-op
    }

    /**
     * Rebuilds the gas throttle based on the current configuration.
     */
    public void applyGasConfig() {
        final var configuration = configSupplier.get();
        final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
        if (contractsConfig.throttleThrottleByGas() && contractsConfig.maxGasPerSec() == 0) {
            log.warn("{} gas throttling enabled, but limited to 0 gas/sec", throttleType.name());
        }
        gasThrottle = new GasLimitDeterministicThrottle(contractsConfig.maxGasPerSec());
        if (throttleMetrics != null) {
            throttleMetrics.setupGasThrottleMetric(gasThrottle, configuration);
        }
        if (verbose == Verbose.YES) {
            log.info(
                    "Resolved {} gas throttle -\n {} gas/sec (throttling {})",
                    throttleType.name(),
                    gasThrottle.capacity(),
                    (contractsConfig.throttleThrottleByGas() ? "ON" : "OFF"));
        }
    }

    @NonNull
    private ThrottleGroup<HederaFunctionality> hapiGroupFromPbj(
            @NonNull final com.hedera.hapi.node.transaction.ThrottleGroup pbjThrottleGroup) {
        return new ThrottleGroup<>(pbjThrottleGroup.milliOpsPerSec(), pbjThrottleGroup.operations());
    }

    private void logResolvedDefinitions(final int capacitySplit) {
        if (verbose != Verbose.YES) {
            return;
        }
        var sb = new StringBuilder("Resolved ")
                .append(throttleType.name())
                .append(" ")
                .append("(after splitting capacity ")
                .append(capacitySplit)
                .append(" ways) - \n");
        functionReqs.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    var function = entry.getKey();
                    var manager = entry.getValue();
                    sb.append("  ")
                            .append(function)
                            .append(": ")
                            .append(manager.asReadableRequirements())
                            .append("\n");
                });
        log.info("{}", () -> sb.toString().trim());
    }

    /**
     * Gets the gas throttle.
     */
    public @NonNull GasLimitDeterministicThrottle gasLimitThrottle() {
        return requireNonNull(gasThrottle, "");
    }

    public enum ThrottleType {
        FRONTEND_THROTTLE,
        BACKEND_THROTTLE,
        NOOP_THROTTLE,
    }
}
