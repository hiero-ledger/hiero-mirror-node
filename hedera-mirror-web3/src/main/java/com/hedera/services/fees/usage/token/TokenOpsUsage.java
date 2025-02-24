// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.usage.token;

import static com.hedera.services.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hedera.services.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.LONG_SIZE;

import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.fees.usage.token.meta.TokenBurnMeta;
import com.hedera.services.fees.usage.token.meta.TokenCreateMeta;
import com.hedera.services.fees.usage.token.meta.TokenFreezeMeta;
import com.hedera.services.fees.usage.token.meta.TokenMintMeta;
import com.hedera.services.fees.usage.token.meta.TokenPauseMeta;
import com.hedera.services.fees.usage.token.meta.TokenUnfreezeMeta;
import com.hedera.services.fees.usage.token.meta.TokenUnpauseMeta;
import com.hedera.services.fees.usage.token.meta.TokenWipeMeta;
import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.SubType;
import java.util.List;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Remove FeeSchedule, UtilPrng, File logic
 */
public final class TokenOpsUsage {
    /* Sizes of various fee types, _not_ including the collector entity id */
    private static final int FIXED_HBAR_REPR_SIZE = LONG_SIZE;
    private static final int FIXED_HTS_REPR_SIZE = LONG_SIZE + BASIC_ENTITY_ID_SIZE;
    private static final int FRACTIONAL_REPR_SIZE = 4 * LONG_SIZE;
    private static final int ROYALTY_NO_FALLBACK_REPR_SIZE = 2 * LONG_SIZE;
    private static final int ROYALTY_HBAR_FALLBACK_REPR_SIZE = ROYALTY_NO_FALLBACK_REPR_SIZE + FIXED_HBAR_REPR_SIZE;
    private static final int ROYALTY_HTS_FALLBACK_REPR_SIZE = ROYALTY_NO_FALLBACK_REPR_SIZE + FIXED_HTS_REPR_SIZE;

    @SuppressWarnings("java:S3776")
    public int bytesNeededToRepr(final List<CustomFee> feeSchedule) {
        int numFixedHbarFees = 0;
        int numFixedHtsFees = 0;
        int numFractionalFees = 0;
        int numRoyaltyNoFallbackFees = 0;
        int numRoyaltyHtsFallbackFees = 0;
        int numRoyaltyHbarFallbackFees = 0;
        for (final var fee : feeSchedule) {
            if (fee.hasFixedFee()) {
                if (fee.getFixedFee().hasDenominatingTokenId()) {
                    numFixedHtsFees++;
                } else {
                    numFixedHbarFees++;
                }
            } else if (fee.hasFractionalFee()) {
                numFractionalFees++;
            } else {
                final var royaltyFee = fee.getRoyaltyFee();
                if (royaltyFee.hasFallbackFee()) {
                    if (royaltyFee.getFallbackFee().hasDenominatingTokenId()) {
                        numRoyaltyHtsFallbackFees++;
                    } else {
                        numRoyaltyHbarFallbackFees++;
                    }
                } else {
                    numRoyaltyNoFallbackFees++;
                }
            }
        }
        return bytesNeededToRepr(
                numFixedHbarFees,
                numFixedHtsFees,
                numFractionalFees,
                numRoyaltyNoFallbackFees,
                numRoyaltyHtsFallbackFees,
                numRoyaltyHbarFallbackFees);
    }

    public int bytesNeededToRepr(
            final int numFixedHbarFees,
            final int numFixedHtsFees,
            final int numFractionalFees,
            final int numRoyaltyNoFallbackFees,
            final int numRoyaltyHtsFallbackFees,
            final int numRoyaltyHbarFallbackFees) {
        return numFixedHbarFees * plusCollectorSize(FIXED_HBAR_REPR_SIZE)
                + numFixedHtsFees * plusCollectorSize(FIXED_HTS_REPR_SIZE)
                + numFractionalFees * plusCollectorSize(FRACTIONAL_REPR_SIZE)
                + numRoyaltyNoFallbackFees * plusCollectorSize(ROYALTY_NO_FALLBACK_REPR_SIZE)
                + numRoyaltyHtsFallbackFees * plusCollectorSize(ROYALTY_HTS_FALLBACK_REPR_SIZE)
                + numRoyaltyHbarFallbackFees * plusCollectorSize(ROYALTY_HBAR_FALLBACK_REPR_SIZE);
    }

    public void tokenCreateUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenCreateMeta tokenCreateMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);

        accumulator.addBpt(tokenCreateMeta.getBaseSize());
        accumulator.addRbs((tokenCreateMeta.getBaseSize() + tokenCreateMeta.getCustomFeeScheduleSize())
                * tokenCreateMeta.getLifeTime());

        final long tokenSizes = TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(
                        tokenCreateMeta.getNumTokens(),
                        tokenCreateMeta.getFungibleNumTransfers(),
                        tokenCreateMeta.getNftsTransfers())
                * USAGE_PROPERTIES.legacyReceiptStorageSecs();
        accumulator.addRbs(tokenSizes);

        accumulator.addNetworkRbs(tokenCreateMeta.getNetworkRecordRb() * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    public void tokenBurnUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenBurnMeta tokenBurnMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);

        accumulator.addBpt(tokenBurnMeta.getBpt());
        accumulator.addNetworkRbs(tokenBurnMeta.getTransferRecordDb() * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    public void tokenMintUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenMintMeta tokenMintMeta,
            final UsageAccumulator accumulator,
            final SubType subType) {
        if (SubType.TOKEN_NON_FUNGIBLE_UNIQUE.equals(subType)) {
            accumulator.reset();
        } else {
            accumulator.resetForTransaction(baseMeta, sigUsage);
        }

        accumulator.addBpt(tokenMintMeta.getBpt());
        accumulator.addRbs(tokenMintMeta.getRbs());
        accumulator.addNetworkRbs(tokenMintMeta.getTransferRecordDb() * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    public void tokenWipeUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenWipeMeta tokenWipeMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);

        accumulator.addBpt(tokenWipeMeta.getBpt());
        accumulator.addNetworkRbs(tokenWipeMeta.getTransferRecordDb() * USAGE_PROPERTIES.legacyReceiptStorageSecs());
    }

    public void tokenFreezeUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenFreezeMeta tokenFreezeMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);

        accumulator.addBpt(tokenFreezeMeta.getBpt());
    }

    public void tokenUnfreezeUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenUnfreezeMeta tokenUnfreezeMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);

        accumulator.addBpt(tokenUnfreezeMeta.getBpt());
    }

    public void tokenPauseUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenPauseMeta tokenPauseMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);

        accumulator.addBpt(tokenPauseMeta.getBpt());
    }

    public void tokenUnpauseUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final TokenUnpauseMeta tokenUnpauseMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);

        accumulator.addBpt(tokenUnpauseMeta.getBpt());
    }

    private int plusCollectorSize(final int feeReprSize) {
        return feeReprSize + BASIC_ENTITY_ID_SIZE;
    }
}
