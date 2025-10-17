// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.config;

import static org.hiero.mirror.common.util.DomainUtils.fromEvmAddress;

import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperation;
import com.hedera.node.app.service.evm.contracts.operations.HederaExtCodeHashOperationV038;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calc.OverflowCheckingCalc;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenAssociateResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenDeleteResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenDissociateResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenUpdateResourceUsage;
import com.hedera.services.fees.calculation.utils.AccessorBasedUsages;
import com.hedera.services.fees.calculation.utils.OpUsageCtxHelper;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.fees.usage.token.TokenOpsUsage;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.services.hapi.fees.usage.crypto.CryptoOpsUsage;
import com.hedera.services.hapi.utils.fees.CryptoFeeBuilder;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.AllowancePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DeleteTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.FreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetApprovedPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GrantKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.IsApprovedForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.PausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.RevokeKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SetApprovalForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenUpdateKeysPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenUpdatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnfreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnpausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.txn.token.BurnLogic;
import com.hedera.services.txn.token.CreateLogic;
import com.hedera.services.txn.token.DeleteLogic;
import com.hedera.services.txn.token.DissociateLogic;
import com.hedera.services.txn.token.FreezeLogic;
import com.hedera.services.txn.token.GrantKycLogic;
import com.hedera.services.txn.token.MintLogic;
import com.hedera.services.txn.token.PauseLogic;
import com.hedera.services.txn.token.RevokeKycLogic;
import com.hedera.services.txn.token.UnfreezeLogic;
import com.hedera.services.txn.token.UnpauseLogic;
import com.hedera.services.txn.token.WipeLogic;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.crypto.DeleteAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.txns.token.validators.CreateChecks;
import com.hedera.services.txns.util.PrngLogic;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.evm.account.MirrorEvmContractAliases;
import org.hiero.mirror.web3.evm.pricing.RatesAndFeesLoader;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.store.Store;
import org.hiero.mirror.web3.evm.store.contract.EntityAddressSequencer;
import org.hiero.mirror.web3.evm.store.contract.MirrorEntityAccess;
import org.hiero.mirror.web3.evm.token.TokenAccessorImpl;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.utils.AccountDetector;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for beans related to com.hedera.services components
 */
@Configuration
public class ServicesConfiguration {

    @Bean
    static Predicate<Address> systemAccountDetector() {
        // all addresses between 0-750 (inclusive) are treated as system accounts
        // from the perspective of the EVM when executing Call, Balance, and SelfDestruct operations
        return address -> {
            var entityId = fromEvmAddress(address.toArray());
            return entityId != null && AccountDetector.isSystem(entityId.getNum());
        };
    }

    @Bean
    static Predicate<Address> strictSystemAccountDetector() {
        // all addresses between 0-999 (inclusive) are treated as system accounts
        // from the perspective of the EVM when executing ExtCode operations
        return address -> {
            var entityId = fromEvmAddress(address.toArray());
            return entityId != null && AccountDetector.isStrictSystem(entityId.getNum());
        };
    }

    @Bean
    GasCalculatorHederaV22 gasCalculatorHederaV22(
            final BasicFcfsUsagePrices usagePricesProvider, final BasicHbarCentExchange hbarCentExchange) {
        return new GasCalculatorHederaV22(usagePricesProvider, hbarCentExchange);
    }

    @Bean
    BasicFcfsUsagePrices basicFcfsUsagePrices(final RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicFcfsUsagePrices(ratesAndFeesLoader);
    }

    @Bean
    OverflowCheckingCalc overflowCheckingCalc() {
        return new OverflowCheckingCalc();
    }

    @Bean
    PricedUsageCalculator pricedUsageCalculator(
            final AccessorBasedUsages accessorBasedUsages, final OverflowCheckingCalc overflowCheckingCalc) {
        return new PricedUsageCalculator(accessorBasedUsages, overflowCheckingCalc);
    }

    @Bean
    EstimatorFactory estimatorFactory() {
        return TxnUsageEstimator::new;
    }

    @Bean
    TokenAssociateResourceUsage tokenAssociateResourceUsage(
            final EstimatorFactory estimatorFactory, final Store store) {
        return new TokenAssociateResourceUsage(estimatorFactory, store);
    }

    @Bean
    TokenDeleteResourceUsage tokenDeleteResourceUsage(final EstimatorFactory estimatorFactory) {
        return new TokenDeleteResourceUsage(estimatorFactory);
    }

    @Bean
    TokenDissociateResourceUsage tokenDissociateResourceUsage(
            final EstimatorFactory estimatorFactory, final Store store) {
        return new TokenDissociateResourceUsage(estimatorFactory, store);
    }

    @Bean
    TokenUpdateResourceUsage tokenUpdateResourceUsage(final EstimatorFactory estimatorFactory, final Store store) {
        return new TokenUpdateResourceUsage(estimatorFactory, store);
    }

    @Bean
    UsageBasedFeeCalculator usageBasedFeeCalculator(
            final HbarCentExchange hbarCentExchange,
            final UsagePricesProvider usagePricesProvider,
            final PricedUsageCalculator pricedUsageCalculator,
            final Set<QueryResourceUsageEstimator> queryResourceUsageEstimators,
            final List<TxnResourceUsageEstimator> txnResourceUsageEstimators) {
        final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators =
                new EnumMap<>(HederaFunctionality.class);

        for (final var estimator : txnResourceUsageEstimators) {
            if (estimator.toString().contains("TokenAssociate")) {
                txnUsageEstimators.put(HederaFunctionality.TokenAssociateToAccount, List.of(estimator));
            }
            if (estimator.toString().contains("TokenDissociate")) {
                txnUsageEstimators.put(HederaFunctionality.TokenDissociateFromAccount, List.of(estimator));
            }
            if (estimator.toString().contains("TokenDelete")) {
                txnUsageEstimators.put(HederaFunctionality.TokenDelete, List.of(estimator));
            }
            if (estimator.toString().contains("TokenUpdate")) {
                txnUsageEstimators.put(HederaFunctionality.TokenUpdate, List.of(estimator));
            }
        }

        return new UsageBasedFeeCalculator(
                hbarCentExchange,
                usagePricesProvider,
                pricedUsageCalculator,
                queryResourceUsageEstimators,
                txnUsageEstimators);
    }

    @Bean
    LivePricesSource livePricesSource(
            final HbarCentExchange hbarCentExchange, final UsagePricesProvider usagePricesProvider) {
        return new LivePricesSource(hbarCentExchange, usagePricesProvider);
    }

    @Bean
    ExpandHandleSpanMapAccessor expandHandleSpanMapAccessor() {
        return new ExpandHandleSpanMapAccessor();
    }

    @Bean
    AssetsLoader assetsLoader() {
        return new AssetsLoader();
    }

    @Bean
    AccessorFactory accessorFactory() {
        return new AccessorFactory();
    }

    @Bean
    PrecompilePricingUtils precompilePricingUtils(
            final AssetsLoader assetsLoader,
            final BasicHbarCentExchange exchange,
            final FeeCalculator feeCalculator,
            final BasicFcfsUsagePrices resourceCosts,
            final AccessorFactory accessorFactory) {
        return new PrecompilePricingUtils(assetsLoader, exchange, feeCalculator, resourceCosts, accessorFactory);
    }

    @Bean
    CryptoFeeBuilder cryptoFeeBuilder() {
        return new CryptoFeeBuilder();
    }

    @Bean
    GetTxnRecordResourceUsage getTxnRecordResourceUsage(final CryptoFeeBuilder cryptoFeeBuilder) {
        return new GetTxnRecordResourceUsage(cryptoFeeBuilder);
    }

    @Bean
    BasicHbarCentExchange basicHbarCentExchange(final RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicHbarCentExchange(ratesAndFeesLoader);
    }

    @Bean
    AbstractCodeCache abstractCodeCache(
            final MirrorNodeEvmProperties evmProperties, final MirrorEntityAccess mirrorEntityAccess) {
        return new AbstractCodeCache(
                (int) evmProperties.getExpirationCacheTime().toSeconds(), mirrorEntityAccess);
    }

    @Bean
    GetApprovedPrecompile getApprovedPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        return new GetApprovedPrecompile(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Bean
    AllowancePrecompile allowancePrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        return new AllowancePrecompile(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Bean
    IsApprovedForAllPrecompile isApprovedForAllPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        return new IsApprovedForAllPrecompile(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Bean
    AssociatePrecompile associatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final AssociateLogic associateLogic) {
        return new AssociatePrecompile(precompilePricingUtils, syntheticTxnFactory, associateLogic);
    }

    @Bean
    MultiAssociatePrecompile multiAssociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final AssociateLogic associateLogic) {
        return new MultiAssociatePrecompile(precompilePricingUtils, syntheticTxnFactory, associateLogic);
    }

    @Bean
    PrecompileMapper precompileMapper(final Set<Precompile> precompiles) {
        return new PrecompileMapper(precompiles);
    }

    @Bean
    OptionValidator optionValidator(final MirrorNodeEvmProperties properties) {
        return new ContextOptionValidator(properties);
    }

    @Bean
    EncodingFacade encodingFacade() {
        return new EncodingFacade();
    }

    @Bean
    SyntheticTxnFactory syntheticTxnFactory() {
        return new SyntheticTxnFactory();
    }

    @Bean
    DeleteAllowanceLogic deleteAllowanceLogic() {
        return new DeleteAllowanceLogic();
    }

    @Bean
    AssociateLogic associateLogic(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new AssociateLogic(mirrorNodeEvmProperties);
    }

    @Bean
    ApproveAllowanceLogic approveAllowanceLogic() {
        return new ApproveAllowanceLogic();
    }

    @Bean
    AutoCreationLogic autocreationLogic(
            final FeeCalculator feeCalculator,
            final EvmProperties evmProperties,
            final SyntheticTxnFactory syntheticTxnFactory,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        return new AutoCreationLogic(feeCalculator, evmProperties, syntheticTxnFactory, mirrorEvmContractAliases);
    }

    @Bean
    MintPrecompile mintPrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final EncodingFacade encodingFacade,
            final SyntheticTxnFactory syntheticTxnFactory,
            final MintLogic mintLogic) {
        return new MintPrecompile(precompilePricingUtils, encodingFacade, syntheticTxnFactory, mintLogic);
    }

    @Bean
    MintLogic mintLogic(final OptionValidator optionValidator) {
        return new MintLogic(optionValidator);
    }

    @Bean
    BurnLogic burnLogic(final OptionValidator optionValidator) {
        return new BurnLogic(optionValidator);
    }

    @Bean
    DissociateLogic dissociateLogic() {
        return new DissociateLogic();
    }

    @Bean
    TransferLogic transferLogic(
            final AutoCreationLogic autoCreationLogic, final MirrorEvmContractAliases mirrorEvmContractAliases) {
        return new TransferLogic(autoCreationLogic, mirrorEvmContractAliases);
    }

    @Bean
    TransferPrecompile transferPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final TransferLogic transferLogic,
            final ContextOptionValidator contextOptionValidator,
            final AutoCreationLogic autoCreationLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EntityAddressSequencer entityAddressSequencer,
            final Predicate<Address> systemAccountDetector) {
        return new TransferPrecompile(
                pricingUtils,
                mirrorNodeEvmProperties,
                transferLogic,
                contextOptionValidator,
                autoCreationLogic,
                syntheticTxnFactory,
                entityAddressSequencer,
                systemAccountDetector);
    }

    @Bean
    ERCTransferPrecompile ercTransferPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final TransferLogic transferLogic,
            final ContextOptionValidator contextOptionValidator,
            final AutoCreationLogic autoCreationLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final EntityAddressSequencer entityAddressSequencer,
            final Predicate<Address> systemAccountDetector) {
        return new ERCTransferPrecompile(
                pricingUtils,
                mirrorNodeEvmProperties,
                transferLogic,
                contextOptionValidator,
                autoCreationLogic,
                syntheticTxnFactory,
                entityAddressSequencer,
                encoder,
                systemAccountDetector);
    }

    @Bean
    DissociatePrecompile dissociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final DissociateLogic dissociateLogic) {
        return new DissociatePrecompile(precompilePricingUtils, syntheticTxnFactory, dissociateLogic);
    }

    @Bean
    MultiDissociatePrecompile multiDissociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final DissociateLogic dissociateLogic) {
        return new MultiDissociatePrecompile(precompilePricingUtils, syntheticTxnFactory, dissociateLogic);
    }

    @Bean
    CreateLogic createLogic(
            final MirrorNodeEvmProperties mirrorNodeEvmProperties, final AssociateLogic associateLogic) {
        return new CreateLogic(mirrorNodeEvmProperties, associateLogic);
    }

    @Bean
    GrantKycLogic grantKycLogic() {
        return new GrantKycLogic();
    }

    @Bean
    GrantKycPrecompile grantKycPrecompile(
            final GrantKycLogic grantKycLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils) {
        return new GrantKycPrecompile(grantKycLogic, syntheticTxnFactory, pricingUtils);
    }

    @Bean
    BurnPrecompile burnPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final BurnLogic burnLogic) {
        return new BurnPrecompile(pricingUtils, encoder, syntheticTxnFactory, burnLogic);
    }

    @Bean
    ApproveAllowanceChecks approveAllowanceChecks() {
        return new ApproveAllowanceChecks();
    }

    @Bean
    DeleteAllowanceChecks deleteAllowanceChecks() {
        return new DeleteAllowanceChecks();
    }

    @Bean
    ApprovePrecompile approvePrecompile(
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final ApproveAllowanceLogic approveAllowanceLogic,
            final DeleteAllowanceLogic deleteAllowanceLogic,
            final ApproveAllowanceChecks approveAllowanceChecks,
            final DeleteAllowanceChecks deleteAllowanceChecks) {
        return new ApprovePrecompile(
                encoder,
                syntheticTxnFactory,
                pricingUtils,
                approveAllowanceLogic,
                deleteAllowanceLogic,
                approveAllowanceChecks,
                deleteAllowanceChecks);
    }

    @Bean
    TokenOpsUsage tokenOpsUsage() {
        return new TokenOpsUsage();
    }

    @Bean
    CryptoOpsUsage cryptoOpsUsage() {
        return new CryptoOpsUsage();
    }

    @Bean
    OpUsageCtxHelper opUsageCtxHelper(final Store store, final HederaEvmContractAliases hederaEvmContractAliases) {
        return new OpUsageCtxHelper(store, hederaEvmContractAliases);
    }

    @Bean
    AccessorBasedUsages accessorBasedUsages(
            final TokenOpsUsage tokenOpsUsage,
            final CryptoOpsUsage cryptoOpsUsage,
            final OpUsageCtxHelper opUsageCtxHelper,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new AccessorBasedUsages(tokenOpsUsage, cryptoOpsUsage, opUsageCtxHelper, mirrorNodeEvmProperties);
    }

    @Bean
    WipeLogic wipeLogic(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new WipeLogic(mirrorNodeEvmProperties);
    }

    @Bean
    WipeFungiblePrecompile wipeFungiblePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WipeLogic wipeLogic) {
        return new WipeFungiblePrecompile(precompilePricingUtils, syntheticTxnFactory, wipeLogic);
    }

    @Bean
    WipeNonFungiblePrecompile wipeNonFungiblePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WipeLogic wipeLogic) {
        return new WipeNonFungiblePrecompile(precompilePricingUtils, syntheticTxnFactory, wipeLogic);
    }

    @Bean
    RevokeKycLogic revokeKycLogic() {
        return new RevokeKycLogic();
    }

    @Bean
    RevokeKycPrecompile revokeKycPrecompile(
            final RevokeKycLogic revokeKycLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils precompilePricingUtils) {
        return new RevokeKycPrecompile(revokeKycLogic, syntheticTxnFactory, precompilePricingUtils);
    }

    @Bean
    TokenCreatePrecompile tokenCreatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final EncodingFacade encodingFacade,
            final SyntheticTxnFactory syntheticTxnFactory,
            final OptionValidator validator,
            final CreateLogic createLogic,
            final FeeCalculator feeCalculator,
            final CreateChecks createChecks) {
        return new TokenCreatePrecompile(
                precompilePricingUtils,
                encodingFacade,
                syntheticTxnFactory,
                validator,
                createLogic,
                feeCalculator,
                createChecks);
    }

    @Bean
    SetApprovalForAllPrecompile setApprovalForAllPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final ApproveAllowanceChecks approveAllowanceChecks,
            final ApproveAllowanceLogic approveAllowanceLogic) {
        return new SetApprovalForAllPrecompile(
                syntheticTxnFactory, pricingUtils, approveAllowanceChecks, approveAllowanceLogic);
    }

    @Bean
    DeleteLogic deleteLogic() {
        return new DeleteLogic();
    }

    @Bean
    DeleteTokenPrecompile deleteTokenPrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final DeleteLogic deleteLogic) {
        return new DeleteTokenPrecompile(precompilePricingUtils, syntheticTxnFactory, deleteLogic);
    }

    @Bean
    UnpauseLogic unpauseLogic() {
        return new UnpauseLogic();
    }

    @Bean
    UnpausePrecompile unpausePrecompile(
            final UnpauseLogic unpauseLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils) {
        return new UnpausePrecompile(pricingUtils, syntheticTxnFactory, unpauseLogic);
    }

    @Bean
    FreezeLogic freezeLogic() {
        return new FreezeLogic();
    }

    @Bean
    FreezeTokenPrecompile freezeTokenPrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final FreezeLogic freezeLogic) {
        return new FreezeTokenPrecompile(precompilePricingUtils, syntheticTxnFactory, freezeLogic);
    }

    @Bean
    UnfreezeLogic unfreezeLogic() {
        return new UnfreezeLogic();
    }

    @Bean
    UnfreezeTokenPrecompile unfreezeTokenPrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final UnfreezeLogic unfreezeLogic) {
        return new UnfreezeTokenPrecompile(precompilePricingUtils, syntheticTxnFactory, unfreezeLogic);
    }

    @Bean
    PauseLogic pauseLogic() {
        return new PauseLogic();
    }

    @Bean
    PausePrecompile pausePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PauseLogic pauseLogic) {
        return new PausePrecompile(precompilePricingUtils, syntheticTxnFactory, pauseLogic);
    }

    @Bean
    TokenUpdateLogic tokenUpdateLogic(
            final MirrorNodeEvmProperties mirrorNodeEvmProperties, final OptionValidator validator) {
        return new TokenUpdateLogic(mirrorNodeEvmProperties, validator);
    }

    @Bean
    TokenUpdatePrecompile tokenUpdatePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final TokenUpdateLogic tokenUpdateLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final ContextOptionValidator contextOptionValidator) {
        return new TokenUpdatePrecompile(
                pricingUtils, tokenUpdateLogic, syntheticTxnFactory, mirrorNodeEvmProperties, contextOptionValidator);
    }

    @Bean
    PrngLogic prngLogic(final RecordFileRepository recordFileRepository) {
        return new PrngLogic(() -> recordFileRepository
                .findLatest()
                .map(RecordFile::getHash)
                .map(Base64.getDecoder()::decode)
                .orElse(null));
    }

    @Bean
    PrngSystemPrecompiledContract prngSystemPrecompiledContract(
            final GasCalculator gasCalculator,
            final PrngLogic prngLogic,
            final LivePricesSource livePricesSource,
            final PrecompilePricingUtils precompilePricingUtils) {
        return new PrngSystemPrecompiledContract(gasCalculator, prngLogic, livePricesSource, precompilePricingUtils);
    }

    @Bean
    UpdateTokenExpiryInfoPrecompile updateTokenExpiryInfoPrecompile(
            final TokenUpdateLogic tokenUpdateLogic,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final ContextOptionValidator contextOptionValidator,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils precompilePricingUtils) {
        return new UpdateTokenExpiryInfoPrecompile(
                tokenUpdateLogic,
                mirrorNodeEvmProperties,
                contextOptionValidator,
                syntheticTxnFactory,
                precompilePricingUtils);
    }

    @Bean
    TokenUpdateKeysPrecompile tokenUpdateKeysPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils precompilePricingUtils,
            final TokenUpdateLogic tokenUpdateLogic,
            final OptionValidator optionValidator,
            final MirrorNodeEvmProperties evmProperties) {
        return new TokenUpdateKeysPrecompile(
                syntheticTxnFactory, precompilePricingUtils, tokenUpdateLogic, optionValidator, evmProperties);
    }

    @Bean
    EvmEncodingFacade evmEncodingFacade() {
        return new EvmEncodingFacade();
    }

    @Bean
    EvmInfrastructureFactory evmInfrastructureFactory() {
        return new EvmInfrastructureFactory(evmEncodingFacade());
    }

    @Bean
    EvmHTSPrecompiledContract evmHTSPrecompiledContract() {
        return new EvmHTSPrecompiledContract(evmInfrastructureFactory());
    }

    @Bean
    HTSPrecompiledContract htsPrecompiledContract(
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final PrecompileMapper precompileMapper,
            final Store store,
            final TokenAccessorImpl tokenAccessor,
            final PrecompilePricingUtils precompilePricingUtils) {
        return new HTSPrecompiledContract(
                evmInfrastructureFactory(),
                mirrorNodeEvmProperties,
                precompileMapper,
                store,
                tokenAccessor,
                precompilePricingUtils);
    }

    @Bean
    HederaExtCodeHashOperationV038 hederaExtCodeHashOperationV038(
            final GasCalculator gasCalculator,
            final Predicate<Address> strictSystemAccountDetector,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new HederaExtCodeHashOperationV038(
                gasCalculator, addressValidator, strictSystemAccountDetector, mirrorNodeEvmProperties);
    }

    @Bean
    HederaExtCodeHashOperation hederaExtCodeHashOperation(
            final GasCalculator gasCalculator, final BiPredicate<Address, MessageFrame> addressValidator) {
        return new HederaExtCodeHashOperation(gasCalculator, addressValidator);
    }

    @Bean
    BiPredicate<Address, MessageFrame> addressValidator(final PrecompilesHolder precompilesHolder) {
        final var precompiles = precompilesHolder.getHederaPrecompiles().keySet().stream()
                .map(Address::fromHexString)
                .collect(Collectors.toSet());
        return (address, frame) ->
                precompiles.contains(address) || frame.getWorldUpdater().get(address) != null;
    }

    @Bean
    CreateChecks createChecks(final OptionValidator optionValidator) {
        return new CreateChecks(optionValidator);
    }
}
