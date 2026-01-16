// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hiero.mirror.web3.service.ContractCallService.GAS_LIMIT_METRIC;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;

import java.math.BigInteger;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.web3j.generated.Reverter;
import org.junit.jupiter.api.Test;

public class RevertTest extends AbstractContractCallServiceTest {

    @Test
    void testRevertPayable() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();
        testWeb3jService.setSender(treasuryAddress);

        final var functionCall = contract.send_revertPayable(BigInteger.ONE);
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "RevertReasonPayable")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013526576657274526561736f6e50617961626c6500000000000000000000000000");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertView() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertView();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "RevertReasonView")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010526576657274526561736f6e5669657700000000000000000000000000000000");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertPure() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertPure();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "RevertReasonPure")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010526576657274526561736f6e5075726500000000000000000000000000000000");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithNothing() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithNothing();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue("data", "0x");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithString() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithString();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "Some revert message")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithCustomError() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithCustomError();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue("data", "0x0bd3d39c");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithCustomErrorWithParameters() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithCustomErrorWithParameters();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0xcc4263a0000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithPanic() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithPanic();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue(
                        "data", "0x4e487b710000000000000000000000000000000000000000000000000000000000000012");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithNothingPure() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithNothingPure();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue("data", "0x");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithStringPure() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithStringPure();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "Some revert message")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0x08c379a000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithCustomErrorPure() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithCustomErrorPure();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue("data", "0x0bd3d39c");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithCustomErrorWithParametersPure() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithCustomErrorWithParametersPure();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue(
                        "data",
                        "0xcc4263a0000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000013536f6d6520726576657274206d65737361676500000000000000000000000000");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void testRevertWithPanicPure() {
        final var contract = testWeb3jService.deploy(Reverter::deploy);
        meterRegistry.clear();

        final var functionCall = contract.send_revertWithPanicPure();
        assertThatThrownBy(functionCall::send)
                .isInstanceOf(MirrorEvmTransactionException.class)
                .hasMessage(CONTRACT_REVERT_EXECUTED.name())
                .hasFieldOrPropertyWithValue("detail", "")
                .hasFieldOrPropertyWithValue(
                        "data", "0x4e487b710000000000000000000000000000000000000000000000000000000000000012");
        assertGasLimit(TRANSACTION_GAS_LIMIT);
    }

    private void assertGasLimit(final long gasLimit) {
        final var counter = meterRegistry.find(GAS_LIMIT_METRIC).counters().stream()
                .filter(c -> ETH_CALL.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        assertThat(counter.count()).isEqualTo(gasLimit);
    }
}
