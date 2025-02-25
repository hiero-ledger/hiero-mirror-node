// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PRNG;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.util.PrngLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import jakarta.annotation.Nonnull;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/**
 * This is a modified copy of the PRNGSystemPrecompiledContract class from the hedera-services repository.
 * <p>
 * The main differences from the original version are as follows: 1. The seed is generated based on the running hash of
 * the latest record file retrieved from the database. 2. The childRecord logic has been removed.
 */
public class PrngSystemPrecompiledContract extends AbstractPrecompiledContract {
    // random256BitGenerator(uint256)
    public static final int PSEUDORANDOM_SEED_GENERATOR_SELECTOR = 0xd83bf9a1;
    public static final String PRNG_PRECOMPILE_ADDRESS = "0x169";
    private static final Logger log = LogManager.getLogger(PrngSystemPrecompiledContract.class);
    private static final String PRECOMPILE_NAME = "PRNG";
    private final LivePricesSource livePricesSource;
    private final PrecompilePricingUtils pricingUtils;
    private final PrngLogic prngLogic;

    private long gasRequirement;

    public PrngSystemPrecompiledContract(
            final GasCalculator gasCalculator,
            final PrngLogic prngLogic,
            final LivePricesSource livePricesSource,
            final PrecompilePricingUtils pricingUtils) {
        super(PRECOMPILE_NAME, gasCalculator);
        this.livePricesSource = livePricesSource;
        this.prngLogic = prngLogic;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @Override
    @Nonnull
    public PrecompileContractResult computePrecompile(final Bytes input, final MessageFrame frame) {
        gasRequirement =
                calculateGas(Instant.ofEpochSecond(frame.getBlockValues().getTimestamp()));
        final var result = computePrngResult(gasRequirement, input, frame);
        return result.getLeft();
    }

    public Pair<PrecompileContractResult, ResponseCodeEnum> computePrngResult(
            final long gasNeeded, final Bytes input, final MessageFrame frame) {
        try {
            validateTrue(input.size() >= 4, INVALID_TRANSACTION_BODY);
            validateTrue(frame.getValue().getAsBigInteger().equals(BigInteger.ZERO), INVALID_FEE_SUBMITTED);
            validateTrue(frame.getRemainingGas() >= gasNeeded, INSUFFICIENT_GAS);
            final var randomNum = generatePseudoRandomData(input);
            return Pair.of(PrecompiledContract.PrecompileContractResult.success(randomNum), null);
        } catch (final InvalidTransactionException e) {
            return Pair.of(
                    PrecompiledContract.PrecompileContractResult.halt(
                            null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    e.getResponseCode());
        } catch (final Exception e) {
            log.warn("Internal precompile failure", e);
            return Pair.of(
                    PrecompiledContract.PrecompileContractResult.halt(
                            null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    FAIL_INVALID);
        }
    }

    public Bytes generatePseudoRandomData(final Bytes input) {
        final var selector = input.getInt(0);
        return selector == PSEUDORANDOM_SEED_GENERATOR_SELECTOR ? random256BitGenerator() : null;
    }

    public long calculateGas(final Instant now) {
        final var feesInTinyCents = pricingUtils.getCanonicalPriceInTinyCents(PRNG);
        final var currentGasPriceInTinyCents = livePricesSource.currentGasPriceInTinycents(now, ContractCall);
        return feesInTinyCents / currentGasPriceInTinyCents;
    }

    private Bytes random256BitGenerator() {
        final var hashBytes = prngLogic.getLatestRecordRunningHashBytes();
        if (isEmptyOrNull(hashBytes)) {
            return null;
        }
        return Bytes.wrap(hashBytes, 0, 32);
    }

    private boolean isEmptyOrNull(final byte[] hashBytes) {
        return hashBytes == null || hashBytes.length == 0;
    }
}
