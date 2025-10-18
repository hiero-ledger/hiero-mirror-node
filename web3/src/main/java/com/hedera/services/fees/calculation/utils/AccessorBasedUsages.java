// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation.utils;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;

import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.fees.usage.token.TokenOpsUsage;
import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.crypto.CryptoOpsUsage;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.EnumSet;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;

/**
 * Copied Logic type from hedera-services. Differences with the original: 1. Remove FeeSchedule, UtilPrng, File logic
 */
@Singleton
public class AccessorBasedUsages {

    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    private static final EnumSet<HederaFunctionality> supportedOps = EnumSet.of(
            CryptoTransfer,
            CryptoCreate,
            CryptoUpdate,
            CryptoApproveAllowance,
            CryptoDeleteAllowance,
            TokenCreate,
            TokenBurn,
            TokenMint,
            TokenAccountWipe,
            TokenFreezeAccount,
            TokenUnfreezeAccount,
            TokenPause,
            TokenUnpause,
            TokenRevokeKycFromAccount,
            TokenGrantKycToAccount);

    private final TokenOpsUsage tokenOpsUsage;
    private final CryptoOpsUsage cryptoOpsUsage;
    private final OpUsageCtxHelper opUsageCtxHelper;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Inject
    public AccessorBasedUsages(
            TokenOpsUsage tokenOpsUsage,
            CryptoOpsUsage cryptoOpsUsage,
            OpUsageCtxHelper opUsageCtxHelper,
            MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.tokenOpsUsage = tokenOpsUsage;
        this.cryptoOpsUsage = cryptoOpsUsage;
        this.opUsageCtxHelper = opUsageCtxHelper;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public void assess(SigUsage sigUsage, TxnAccessor accessor, UsageAccumulator into) {
        final var function = accessor.getFunction();
        if (!supportedOps.contains(function)) {
            throw new IllegalArgumentException("Usage estimation for " + function + " not yet migrated");
        }

        final var baseMeta = accessor.baseUsageMeta();
        if (function == CryptoTransfer) {
            estimateCryptoTransfer(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoCreate) {
            estimateCryptoCreate(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoUpdate) {
            estimateCryptoUpdate(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoApproveAllowance) {
            estimateCryptoApproveAllowance(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoDeleteAllowance) {
            estimateCryptoDeleteAllowance(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenCreate) {
            estimateTokenCreate(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenBurn) {
            estimateTokenBurn(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenMint) {
            estimateTokenMint(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenFreezeAccount) {
            estimateTokenFreezeAccount(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenUnfreezeAccount) {
            estimateTokenUnfreezeAccount(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenAccountWipe) {
            estimateTokenWipe(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenPause) {
            estimateTokenPause(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenUnpause) {
            estimateTokenUnpause(sigUsage, accessor, baseMeta, into);
        }
    }

    public boolean supports(HederaFunctionality function) {
        return supportedOps.contains(function);
    }

    private void estimateCryptoTransfer(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var xferMeta = accessor.availXferUsageMeta();
        xferMeta.setTokenMultiplier(mirrorNodeEvmProperties.feesTokenTransferUsageMultiplier());
        cryptoOpsUsage.cryptoTransferUsage(sigUsage, xferMeta, baseMeta, into);
    }

    private void estimateCryptoCreate(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var cryptoCreateMeta = accessor.getSpanMapAccessor().getCryptoCreateMeta(accessor);
        cryptoOpsUsage.cryptoCreateUsage(sigUsage, baseMeta, cryptoCreateMeta, into);
    }

    private void estimateCryptoUpdate(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var cryptoUpdateMeta = accessor.getSpanMapAccessor().getCryptoUpdateMeta(accessor);
        final var cryptoContext = opUsageCtxHelper.ctxForCryptoUpdate(accessor.getTxn());
        // explicitAutoAssocSlotLifetime is three months in services
        cryptoOpsUsage.cryptoUpdateUsage(
                sigUsage, baseMeta, cryptoUpdateMeta, cryptoContext, into, THREE_MONTHS_IN_SECONDS);
    }

    private void estimateCryptoApproveAllowance(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var cryptoApproveMeta = accessor.getSpanMapAccessor().getCryptoApproveMeta(accessor);
        final var cryptoContext = opUsageCtxHelper.ctxForCryptoAllowance(accessor);
        cryptoOpsUsage.cryptoApproveAllowanceUsage(sigUsage, baseMeta, cryptoApproveMeta, cryptoContext, into);
    }

    private void estimateCryptoDeleteAllowance(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var cryptoDeleteAllowanceMeta = accessor.getSpanMapAccessor().getCryptoDeleteAllowanceMeta(accessor);
        cryptoOpsUsage.cryptoDeleteAllowanceUsage(sigUsage, baseMeta, cryptoDeleteAllowanceMeta, into);
    }

    private void estimateTokenCreate(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenCreateMeta = accessor.getSpanMapAccessor().getTokenCreateMeta(accessor);
        tokenOpsUsage.tokenCreateUsage(sigUsage, baseMeta, tokenCreateMeta, into);
    }

    private void estimateTokenBurn(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenBurnMeta = accessor.getSpanMapAccessor().getTokenBurnMeta(accessor);
        tokenOpsUsage.tokenBurnUsage(sigUsage, baseMeta, tokenBurnMeta, into);
    }

    private void estimateTokenMint(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenMintMeta = opUsageCtxHelper.metaForTokenMint(accessor);
        tokenOpsUsage.tokenMintUsage(sigUsage, baseMeta, tokenMintMeta, into, accessor.getSubType());
    }

    private void estimateTokenWipe(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenWipeMeta = accessor.getSpanMapAccessor().getTokenWipeMeta(accessor);
        tokenOpsUsage.tokenWipeUsage(sigUsage, baseMeta, tokenWipeMeta, into);
    }

    private void estimateTokenFreezeAccount(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenFreezeMeta = accessor.getSpanMapAccessor().getTokenFreezeMeta(accessor);
        tokenOpsUsage.tokenFreezeUsage(sigUsage, baseMeta, tokenFreezeMeta, into);
    }

    private void estimateTokenUnfreezeAccount(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenUnFreezeMeta = accessor.getSpanMapAccessor().getTokenUnfreezeMeta(accessor);
        tokenOpsUsage.tokenUnfreezeUsage(sigUsage, baseMeta, tokenUnFreezeMeta, into);
    }

    private void estimateTokenPause(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenPauseMeta = accessor.getSpanMapAccessor().getTokenPauseMeta(accessor);
        tokenOpsUsage.tokenPauseUsage(sigUsage, baseMeta, tokenPauseMeta, into);
    }

    private void estimateTokenUnpause(
            SigUsage sigUsage, TxnAccessor accessor, BaseTransactionMeta baseMeta, UsageAccumulator into) {
        final var tokenUnpauseMeta = accessor.getSpanMapAccessor().getTokenUnpauseMeta(accessor);
        tokenOpsUsage.tokenUnpauseUsage(sigUsage, baseMeta, tokenUnpauseMeta, into);
    }
}
