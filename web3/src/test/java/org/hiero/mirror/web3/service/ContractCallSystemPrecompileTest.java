// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.ExchangeRatePrecompile;
import org.hiero.mirror.web3.web3j.generated.PrngSystemContract;
import org.junit.jupiter.api.Test;

class ContractCallSystemPrecompileTest extends AbstractContractCallServiceTest {

    @Test
    void exchangeRatePrecompileTinycentsToTinybarsTestEthCall() throws Exception {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var result =
                contract.call_tinycentsToTinybars(BigInteger.valueOf(100L)).send();
        final var functionCall = contract.send_tinycentsToTinybars(BigInteger.valueOf(100L), BigInteger.ZERO);
        assertThat(result).isEqualTo(BigInteger.valueOf(8L));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void exchangeRatePrecompileTinybarsToTinycentsTestEthCall() throws Exception {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        final var result =
                contract.call_tinybarsToTinycents(BigInteger.valueOf(100L)).send();
        final var functionCall = contract.send_tinybarsToTinycents(BigInteger.valueOf(100L), BigInteger.ZERO);
        assertThat(result).isEqualTo(BigInteger.valueOf(1200L));
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void exchangeRatePrecompileTinycentsToTinybarsTestEthCallAndEstimateWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        testWeb3jService.setSender(treasuryAddress);
        final var functionCall = contract.send_tinycentsToTinybars(BigInteger.valueOf(100L), BigInteger.valueOf(100L));
        String expectedErrorMessage = INVALID_CONTRACT_ID.name();

        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(expectedErrorMessage);
        verifyEstimateGasRevertExecution(functionCall, expectedErrorMessage, MirrorEvmTransactionException.class);
    }

    @Test
    void exchangeRatePrecompileTinybarsToTinycentsTestEthCallAndEstimateWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(ExchangeRatePrecompile::deploy);
        testWeb3jService.setSender(treasuryAddress);
        final var functionCall = contract.send_tinybarsToTinycents(BigInteger.valueOf(100L), BigInteger.valueOf(100L));
        String expectedErrorMessage = INVALID_CONTRACT_ID.name();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(expectedErrorMessage);
        verifyEstimateGasRevertExecution(functionCall, expectedErrorMessage, MirrorEvmTransactionException.class);
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthEstimateGas() {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        final var functionCall = contract.send_getPseudorandomSeed(BigInteger.ZERO);
        verifyEthCallAndEstimateGas(functionCall, contract);
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthEstimateGasWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        testWeb3jService.setSender(treasuryAddress);
        final var functionCall = contract.send_getPseudorandomSeed(BigInteger.valueOf(100));
        String expectedErrorMessage = INVALID_CONTRACT_ID.name();

        verifyEstimateGasRevertExecution(functionCall, expectedErrorMessage, MirrorEvmTransactionException.class);
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCall() throws Exception {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        final var result = contract.call_getPseudorandomSeed().send();
        assertEquals(32, result.length, "The string should represent a 32-byte long array");
    }

    @Test
    void pseudoRandomGeneratorPrecompileFunctionsTestEthCallWithValueRevertExecution() {
        final var contract = testWeb3jService.deploy(PrngSystemContract::deploy);
        testWeb3jService.setSender(treasuryAddress);
        final var functionCall = contract.send_getPseudorandomSeed(BigInteger.valueOf(100));
        String expectedErrorMessage = INVALID_CONTRACT_ID.name();

        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(expectedErrorMessage);
    }
}
