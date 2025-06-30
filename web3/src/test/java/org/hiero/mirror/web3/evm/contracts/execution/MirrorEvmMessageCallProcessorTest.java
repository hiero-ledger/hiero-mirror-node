// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATE;
import static com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract.EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.services.stream.proto.ContractActionType;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.config.PrecompilesHolder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracer;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeTracerOptions;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.ECRECPrecompiledContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class MirrorEvmMessageCallProcessorTest extends MirrorEvmMessageCallProcessorBaseTest {

    private static final Address NON_PRECOMPILE_ADDRESS =
            Address.fromHexString("0x00a94f5374fce5edbc8e2a8697c15331677e6ebf");

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Mock
    private PrecompilesHolder precompilesHolder;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    private OpcodeTracer opcodeTracer;
    private MirrorEvmMessageCallProcessor subject;

    @BeforeEach
    void setUp() {
        when(precompilesHolder.getHederaPrecompiles()).thenReturn(hederaPrecompileList);
        when(messageFrame.getWorldUpdater()).thenReturn(updater);
        opcodeTracer = Mockito.spy(new OpcodeTracer(precompilesHolder, evmProperties));
        subject = new MirrorEvmMessageCallProcessor(
                autoCreationLogic,
                entityAddressSequencer,
                evm,
                precompiles,
                precompilesHolder,
                gasCalculatorHederaV22,
                address -> false);
    }

    @Test
    void executeLazyCreateFailsWithHaltReason() {
        when(updater.getStore()).thenReturn(store);
        when(autoCreationLogic.create(any(), any(), any(), any(), any())).thenReturn(Pair.of(NOT_SUPPORTED, 0L));
        when(messageFrame.getRecipientAddress()).thenReturn(NON_PRECOMPILE_ADDRESS);
        when(messageFrame.getBlockValues()).thenReturn(new HederaBlockValues(0L, 0L, Instant.EPOCH));

        subject.executeLazyCreate(messageFrame, operationTracer);

        verify(messageFrame, times(1)).setState(EXCEPTIONAL_HALT);
        verify(messageFrame, times(1)).decrementRemainingGas(0L);
        verify(operationTracer, times(1))
                .traceAccountCreationResult(messageFrame, Optional.of(FAILURE_DURING_LAZY_ACCOUNT_CREATE));
    }

    @Test
    void executeLazyCreateFailsWithInsufficientGas() {
        when(updater.getStore()).thenReturn(store);
        when(autoCreationLogic.create(any(), any(), any(), any(), any())).thenReturn(Pair.of(OK, 1000L));
        when(messageFrame.getRecipientAddress()).thenReturn(NON_PRECOMPILE_ADDRESS);
        when(messageFrame.getBlockValues()).thenReturn(new HederaBlockValues(0L, 0L, Instant.EPOCH));
        when(messageFrame.getRemainingGas()).thenReturn(0L);
        when(messageFrame.getGasPrice()).thenReturn(Wei.ONE);

        subject.executeLazyCreate(messageFrame, operationTracer);

        verify(messageFrame, times(1)).setState(EXCEPTIONAL_HALT);
        verify(messageFrame, times(1)).decrementRemainingGas(0L);
        verify(operationTracer, times(1))
                .traceAccountCreationResult(messageFrame, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    }

    @Test
    void startWithNativePrecompileTracesPrecompileResult() {
        var contractAddress = Address.ECREC;
        when(messageFrame.getContractAddress()).thenReturn(contractAddress);
        when(messageFrame.getRecipientAddress()).thenReturn(contractAddress);
        when(messageFrame.getValue()).thenReturn(Wei.ZERO);
        when(messageFrame.getInputData()).thenReturn(Bytes.EMPTY);
        when(messageFrame.getState()).thenReturn(MessageFrame.State.COMPLETED_SUCCESS);
        when(precompiles.get(contractAddress)).thenReturn(new ECRECPrecompiledContract(gasCalculatorHederaV22));

        subject.start(messageFrame, opcodeTracer);

        verify(opcodeTracer).tracePrecompileResult(messageFrame, ContractActionType.PRECOMPILE);
    }

    @Test
    void startWithHederaPrecompileTracesPrecompileResult() {
        var contractAddress = Address.fromHexString(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS);
        when(messageFrame.getContractAddress()).thenReturn(contractAddress);
        when(messageFrame.getRecipientAddress()).thenReturn(contractAddress);
        when(messageFrame.getInputData()).thenReturn(Bytes.EMPTY);
        when(messageFrame.getState()).thenReturn(MessageFrame.State.COMPLETED_SUCCESS);

        subject.start(messageFrame, opcodeTracer);

        verify(opcodeTracer).tracePrecompileResult(messageFrame, ContractActionType.SYSTEM);
    }

    @Test
    void startWithNonPrecompileAddressDoesNotTracePrecompileResult() {
        when(messageFrame.getContractAddress()).thenReturn(NON_PRECOMPILE_ADDRESS);
        when(messageFrame.getRecipientAddress()).thenReturn(NON_PRECOMPILE_ADDRESS);
        when(messageFrame.getValue()).thenReturn(Wei.ZERO);

        subject.start(messageFrame, opcodeTracer);

        verify(opcodeTracer, never()).tracePrecompileResult(any(), any());
    }

    @Test
    void startWithNonHTSPrecompiledContractAddress() {
        subject = new MirrorEvmMessageCallProcessor(
                autoCreationLogic,
                entityAddressSequencer,
                evm,
                precompiles,
                precompilesHolder,
                gasCalculatorHederaV22,
                address -> true);
        var contractAddress = Address.fromHexString("0x2EE");
        var recAddress = Address.fromHexString(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS);
        when(messageFrame.getContractAddress()).thenReturn(contractAddress);
        when(messageFrame.getRecipientAddress()).thenReturn(recAddress);
        when(messageFrame.getSenderAddress()).thenReturn(recAddress);
        when(autoCreationLogic.create(any(), any(), any(), any(), any())).thenReturn(Pair.of(OK, 1000L));
        when(messageFrame.getValue()).thenReturn(Wei.of(5000L));
        when(messageFrame.getBlockValues()).thenReturn(new HederaBlockValues(0L, 0L, Instant.EPOCH));
        when(messageFrame.getGasPrice()).thenReturn(Wei.ONE);

        boolean isModularized = evmProperties.isModularizedServices();

        when(contractCallContext.getOpcodeTracerOptions())
                .thenReturn(new OpcodeTracerOptions(true, true, true, isModularized));
        when(messageFrame.getCurrentOperation()).thenReturn(mock(Operation.class));

        subject.start(messageFrame, opcodeTracer);

        verify(messageFrame).setExceptionalHaltReason(Optional.of(HederaExceptionalHaltReason.INVALID_FEE_SUBMITTED));
        inOrder(messageFrame).verify(messageFrame).setState(MessageFrame.State.EXCEPTIONAL_HALT);
    }

    @Test
    void startWithHTSPrecompiledContractAddress() {
        subject = new MirrorEvmMessageCallProcessor(
                autoCreationLogic,
                entityAddressSequencer,
                evm,
                precompiles,
                precompilesHolder,
                gasCalculatorHederaV22,
                address -> true);
        var contractAddress = Address.fromHexString(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS);
        var recAddress = Address.fromHexString(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS);
        when(messageFrame.getContractAddress()).thenReturn(contractAddress);
        when(messageFrame.getRecipientAddress()).thenReturn(recAddress);
        when(messageFrame.getValue()).thenReturn(Wei.of(1000L));

        subject.start(messageFrame, opcodeTracer);

        verify(opcodeTracer).tracePrecompileResult(messageFrame, ContractActionType.SYSTEM);
        assertNull(messageFrame.getState());
    }

    @Test
    void startWithNonHTSAndWithoutValuePrecompiledContractAddress() {
        subject = new MirrorEvmMessageCallProcessor(
                autoCreationLogic,
                entityAddressSequencer,
                evm,
                precompiles,
                precompilesHolder,
                gasCalculatorHederaV22,
                address -> true);
        var contractAddress = Address.fromHexString(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS);
        var recAddress = Address.fromHexString(EVM_HTS_PRECOMPILED_CONTRACT_ADDRESS);
        when(messageFrame.getContractAddress()).thenReturn(contractAddress);
        when(messageFrame.getRecipientAddress()).thenReturn(recAddress);
        when(messageFrame.getValue()).thenReturn(Wei.of(0L));

        subject.start(messageFrame, opcodeTracer);

        verify(opcodeTracer).tracePrecompileResult(messageFrame, ContractActionType.SYSTEM);
        assertNull(messageFrame.getState());
    }
}
