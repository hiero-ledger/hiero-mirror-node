// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.store.contracts.precompile;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FEE_SUBMITTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;

import com.esaulpaugh.headlong.abi.BigIntegerType;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.fees.HbarCentExchange;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.jspecify.annotations.NonNull;

/**
 * System contract to interconvert tinybars and tinycents at the active exchange rate. The ABI consists of 1 to 9 packed
 * bytes where,
 *
 * <ol>
 *   <li>The first byte is either {@code 0xbb}, when converting to tinybars; or {@code 0xcc}, when
 *       converting to tinycents.
 *   <li>The remaining 0 to 8 bytes are (logically) left-padded with zeros to form an eight-byte
 *       big-endian representation of a {@code long} value.
 * </ol>
 *
 * <p>When the input {@code Bytes} take this form, <i>and</i> the given value can be converted to
 * the requested denomination without over-flowing an eight-byte value, the contract returns the
 * conversion result. Otherwise, it returns null.
 */
public class ExchangeRatePrecompiledContract extends AbstractPrecompiledContract {
    public static final String EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS = "0x168";
    // tinycentsToTinybars(uint256)
    static final int TO_TINYBARS_SELECTOR = 0x2e3cff6a;
    // tinybarsToTinycents(uint256)
    static final int TO_TINYCENTS_SELECTOR = 0x43a88229;
    private static final String PRECOMPILE_NAME = "ExchangeRate";
    private static final BigIntegerType WORD_DECODER = TypeFactory.create("uint256");
    private final HbarCentExchange exchange;
    private final Instant consensusNow;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public ExchangeRatePrecompiledContract(
            final GasCalculator gasCalculator,
            final HbarCentExchange exchange,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final Instant consensusNow) {
        super(PRECOMPILE_NAME, gasCalculator);
        this.exchange = exchange;
        this.consensusNow = consensusNow;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    @Override
    public long gasRequirement(Bytes bytes) {
        return mirrorNodeEvmProperties.exchangeRateGasReq();
    }

    @Override
    public @NonNull PrecompileContractResult computePrecompile(final Bytes input, final @NonNull MessageFrame frame) {
        try {
            validateTrue(input.size() >= 4, INVALID_TRANSACTION_BODY);
            validateTrue(frame.getValue().getAsBigInteger().equals(BigInteger.ZERO), INVALID_FEE_SUBMITTED);
            final var selector = input.getInt(0);
            final var amount = biValueFrom(input);
            final var activeRate = exchange.activeRate(consensusNow);
            return switch (selector) {
                case TO_TINYBARS_SELECTOR ->
                    padded(fromAToB(amount, activeRate.getHbarEquiv(), activeRate.getCentEquiv()));
                case TO_TINYCENTS_SELECTOR ->
                    padded(fromAToB(amount, activeRate.getCentEquiv(), activeRate.getHbarEquiv()));
                default -> PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE));
            };
        } catch (Exception ignore) {
            return PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE));
        }
    }

    private BigInteger fromAToB(final BigInteger aAmount, final int bEquiv, final int aEquiv) {
        return aAmount.multiply(BigInteger.valueOf(bEquiv)).divide(BigInteger.valueOf(aEquiv));
    }

    private BigInteger biValueFrom(final Bytes input) {
        return WORD_DECODER.decode(input.slice(4).toArrayUnsafe());
    }

    private PrecompileContractResult padded(final BigInteger result) {
        return PrecompileContractResult.success(Bytes32.leftPad(Bytes.wrap(result.toByteArray())));
    }
}
