// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.store.contract.precompile;

import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PSEUDORANDOM_SEED_GENERATOR_SELECTOR;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PRNG;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.util.PrngLogic;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.utility.CommonUtils;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrngSystemPrecompiledContractTest {
    private static final byte[] WELL_KNOWN_HASH_BYTE_ARRAY = CommonUtils.unhex(
            "65386630386164632d356537632d343964342d623437372d62636134346538386338373133633038316162372d6163");
    private final Instant consensusNow = Instant.ofEpochSecond(123456789L);

    @Mock
    private MessageFrame frame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private PrecompilePricingUtils pricingUtils;

    @Mock
    private LivePricesSource livePricesSource;

    private PrngSystemPrecompiledContract subject;

    private static Bytes random256BitGeneratorInput() {
        return input(PSEUDORANDOM_SEED_GENERATOR_SELECTOR);
    }

    private static Bytes input(final int selector) {
        return Bytes.concatenate(Bytes.ofUnsignedInt(selector & 0xffffffffL), Bytes.EMPTY);
    }

    @BeforeEach
    void setUp() {
        Supplier<byte[]> mockSupplier = () -> WELL_KNOWN_HASH_BYTE_ARRAY;
        final var logic = new PrngLogic(mockSupplier);

        subject = new PrngSystemPrecompiledContract(gasCalculator, logic, livePricesSource, pricingUtils);
    }

    @Test
    void generatesRandom256BitNumber() {
        final var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertEquals(32, result.toArray().length);
    }

    @Test
    void testComputePrngResult_Throws_InvalidTransactionException() {
        // Given low remaining gas
        given(frame.getRemainingGas()).willReturn(500L);
        given(frame.getValue()).willReturn(Wei.ZERO);

        // When
        final var result = subject.computePrngResult(1000L, random256BitGeneratorInput(), frame);

        // Then
        assertNotNull(result);
        assertEquals(
                ExceptionalHaltReason.INVALID_OPERATION,
                result.getLeft().getHaltReason().orElse(null));
        assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS, result.getRight());
    }

    @Test
    void testComputePrngResultWithValueThrowsInvalidTransactionException() {
        given(frame.getValue()).willReturn(Wei.ONE);

        // When
        final var result = subject.computePrngResult(10L, random256BitGeneratorInput(), frame);

        // Then
        assertNotNull(result);
        assertEquals(
                ExceptionalHaltReason.INVALID_OPERATION,
                result.getLeft().getHaltReason().orElse(null));
        assertEquals(ResponseCodeEnum.INVALID_FEE_SUBMITTED, result.getRight());
    }

    @Test
    void calculatesGasCorrectly() {
        given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
        given(livePricesSource.currentGasPriceInTinycents(consensusNow, HederaFunctionality.ContractCall))
                .willReturn(800L);
        assertEquals(100000000L / 800L, subject.calculateGas(consensusNow));
    }

    @Test
    void happyPathWithRandomSeedGeneratedWorks() {
        final var input = random256BitGeneratorInput();
        initialSetUp();

        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, consensusNow));
        given(frame.getValue()).willReturn(Wei.ZERO);

        final var response = subject.computePrngResult(10L, input, frame);
        assertEquals(Optional.empty(), response.getLeft().getHaltReason());
        assertEquals(COMPLETED_SUCCESS, response.getLeft().getState());
        assertNull(response.getRight());

        final var result = subject.computePrecompile(input, frame);
        assertNotNull(result.getOutput());
    }

    @Test
    void selectorMustBeRecognized() {
        final var fragmentSelector = Bytes.of((byte) 0xab, (byte) 0xab, (byte) 0xab, (byte) 0xab);
        final var input = Bytes.concatenate(fragmentSelector, Bytes32.ZERO);
        assertNull(subject.generatePseudoRandomData(input));
    }

    @Test
    void nullHashReturnsSentinelOutputs() {
        // Override the Supplier to return null for this test
        Supplier<byte[]> nullReturningSupplier = () -> null;
        final var prngLogic = new PrngLogic(nullReturningSupplier);
        subject = new PrngSystemPrecompiledContract(gasCalculator, prngLogic, livePricesSource, pricingUtils);

        final var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertNull(result);
    }

    @Test
    void zeroedHashReturnsSentinelOutputs() {
        // Override the Supplier to return zeroed hash for this test
        Supplier<byte[]> nullReturningSupplier = () -> new byte[48];
        final var prngLogic = new PrngLogic(nullReturningSupplier);
        subject = new PrngSystemPrecompiledContract(gasCalculator, prngLogic, livePricesSource, pricingUtils);

        final var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertNull(result);
    }

    private void initialSetUp() {
        given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
        given(livePricesSource.currentGasPriceInTinycents(consensusNow, HederaFunctionality.ContractCall))
                .willReturn(830L);
        given(frame.getRemainingGas()).willReturn(400_000L);
    }
}
