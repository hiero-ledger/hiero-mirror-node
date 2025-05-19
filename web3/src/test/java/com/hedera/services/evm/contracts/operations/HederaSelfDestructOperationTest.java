// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.evm.contracts.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import java.util.function.BiPredicate;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaSelfDestructOperationTest {

    private static final Address BENEFICIARY = Address.fromHexString("0x0000000000000000000000000000000000000929");
    private static final String ETH_ADDRESS = "0xc257274276a4e539741ca11b590b9447b26a8051";
    private static final Address EIP_1014_ETH_ADDRESS = Address.fromHexString(ETH_ADDRESS);

    @Mock
    private HederaEvmStackedWorldStateUpdater worldUpdater;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

    @Mock
    private EVM evm;

    @Mock
    private MutableAccount account;

    @Mock
    private BiPredicate<Address, MessageFrame> addressValidator;

    private HederaSelfDestructOperation subject;

    @BeforeEach
    void setUp() {
        subject = new HederaSelfDestructOperation(gasCalculator, addressValidator);

        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(gasCalculator.selfDestructOperationGasCost(any(), eq(Wei.ONE))).willReturn(2L);
    }

    @Test
    void delegatesToSuperWhenValid() {
        givenRubberstampValidator();

        given(frame.getStackItem(0)).willReturn(BENEFICIARY);
        given(frame.popStackItem()).willReturn(BENEFICIARY);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);

        given(worldUpdater.get(any())).willReturn(account);
        given(worldUpdater.getAccount(any())).willReturn(account);
        given(account.getBalance()).willReturn(Wei.ONE);
        given(frame.isStatic()).willReturn(true);
        given(gasCalculator.getColdAccountAccessCost()).willReturn(1L);

        final var opResult = subject.execute(frame, evm);

        assertEquals(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE, opResult.getHaltReason());
        assertEquals(3L, opResult.getGasCost());
    }

    @Test
    void rejectsSelfDestructToSelf() {
        givenRubberstampValidator();

        given(frame.getStackItem(0)).willReturn(EIP_1014_ETH_ADDRESS);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);

        final var opResult = subject.execute(frame, evm);

        assertEquals(HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF, opResult.getHaltReason());
        assertEquals(2L, opResult.getGasCost());
    }

    @Test
    void executeInvalidSolidityAddress() {
        givenRejectingValidator();

        given(frame.getStackItem(0)).willReturn(BENEFICIARY);

        final var opResult = subject.execute(frame, evm);

        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, opResult.getHaltReason());
        assertEquals(2L, opResult.getGasCost());
    }

    @Test
    void rejectsSelfDestructIfTreasury() {
        givenRubberstampValidator();

        given(frame.getStackItem(0)).willReturn(BENEFICIARY);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);
        given(worldUpdater.contractIsTokenTreasury(EIP_1014_ETH_ADDRESS)).willReturn(true);

        final var opResult = subject.execute(frame, evm);

        assertEquals(HederaExceptionalHaltReason.CONTRACT_IS_TREASURY, opResult.getHaltReason());
        assertEquals(2L, opResult.getGasCost());
    }

    @Test
    void rejectsSelfDestructIfContractHasAnyTokenBalance() {
        givenRubberstampValidator();
        given(frame.getStackItem(0)).willReturn(BENEFICIARY);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);
        given(worldUpdater.contractHasAnyBalance(EIP_1014_ETH_ADDRESS)).willReturn(true);

        final var opResult = subject.execute(frame, evm);

        assertEquals(HederaExceptionalHaltReason.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES, opResult.getHaltReason());
        assertEquals(2L, opResult.getGasCost());
    }

    @Test
    void rejectsSelfDestructIfContractHasAnyNfts() {
        givenRubberstampValidator();

        given(frame.getStackItem(0)).willReturn(BENEFICIARY);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ETH_ADDRESS);
        given(worldUpdater.contractOwnsNfts(EIP_1014_ETH_ADDRESS)).willReturn(true);

        final var opResult = subject.execute(frame, evm);

        assertEquals(HederaExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS, opResult.getHaltReason());
        assertEquals(2L, opResult.getGasCost());
    }

    private void givenRubberstampValidator() {
        given(addressValidator.test(any(), any())).willReturn(true);
    }

    private void givenRejectingValidator() {
        given(addressValidator.test(any(), any())).willReturn(false);
    }
}
