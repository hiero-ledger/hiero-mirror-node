// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.TraceProperties;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.frame.MessageFrame.Type;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.BalanceOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MirrorOperationActionTracerTest {

    private static final Address contract = Address.fromHexString("0x2");
    private static final Address recipient = Address.fromHexString("0x3");
    private static final Address sender = Address.fromHexString("0x4");
    private static final long INITIAL_GAS = 1000L;
    private static final Bytes input = Bytes.of("inputData".getBytes());
    private static final Operation operation = new BalanceOperation(null);
    private static final Bytes outputData = Bytes.of("outputData".getBytes());
    private static final Bytes returnData = Bytes.of("returnData".getBytes());
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Spy
    private ContractCallContext contractCallContext;

    @Mock
    private OperationResult operationResult;

    private TraceProperties traceProperties;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private Entity recipientEntity;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    private MirrorOperationActionTracer mirrorOperationTracer;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        traceProperties = new TraceProperties();
        mirrorOperationTracer = new MirrorOperationActionTracer(traceProperties, commonEntityAccessor);
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void traceDisabled(CapturedOutput output) {
        // Given
        traceProperties.setEnabled(false);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(output).doesNotContain("type=");
    }

    @Test
    void stateFilterMismatch(CapturedOutput output) {
        // Given
        traceProperties.setEnabled(true);
        traceProperties.setStatus(Set.of(State.CODE_EXECUTING));
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(output).doesNotContain("type=");
    }

    @Test
    void stateFilter(CapturedOutput output) {
        // Given
        traceProperties.setEnabled(true);
        traceProperties.setStatus(Set.of(State.CODE_SUSPENDED));
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getCurrentOperation()).willReturn(operation);
        given(messageFrame.getRemainingGas()).willReturn(INITIAL_GAS);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getOutputData()).willReturn(outputData);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getReturnData()).willReturn(returnData);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getDepth()).willReturn(1);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(output)
                .contains(
                        "type=MESSAGE_CALL",
                        "callDepth=1",
                        "recipient=0x3",
                        "input=0x696e70757444617461",
                        "operation=BALANCE",
                        "output=0x6f757470757444617461",
                        "remainingGas=1000",
                        "return=0x72657475726e44617461",
                        "revertReason=",
                        "sender=0x4");
    }

    @Test
    void contractFilterMismatch(CapturedOutput output) {
        // Given
        traceProperties.setEnabled(true);
        traceProperties.setContract(Set.of(contract.toHexString()));
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(commonEntityAccessor.get(recipient, Optional.empty())).willReturn(Optional.of(recipientEntity));
        given(recipientEntity.getId()).willReturn(3L);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(output).doesNotContain("type=");
    }

    @Test
    void contractFilter(CapturedOutput output) {
        // Given
        traceProperties.setEnabled(true);
        traceProperties.setContract(Set.of(recipient.toHexString()));
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getCurrentOperation()).willReturn(operation);
        given(messageFrame.getRemainingGas()).willReturn(INITIAL_GAS);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getOutputData()).willReturn(outputData);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getReturnData()).willReturn(returnData);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getDepth()).willReturn(1);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(output)
                .contains(
                        "type=MESSAGE_CALL",
                        "callDepth=1",
                        "recipient=0x3",
                        "input=0x696e70757444617461",
                        "operation=BALANCE",
                        "output=0x6f757470757444617461",
                        "remainingGas=1000",
                        "revertReason=",
                        "return=0x72657475726e44617461",
                        "sender=0x4");
    }

    @Test
    void tracePostExecution(CapturedOutput output) {
        // Given
        traceProperties.setEnabled(true);

        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getCurrentOperation()).willReturn(operation);
        given(messageFrame.getRemainingGas()).willReturn(INITIAL_GAS);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getOutputData()).willReturn(outputData);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getReturnData()).willReturn(returnData);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        given(messageFrame.getType()).willReturn(Type.MESSAGE_CALL);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getCurrentOperation()).willReturn(operation);
        given(messageFrame.getRemainingGas()).willReturn(INITIAL_GAS);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getOutputData()).willReturn(outputData);
        given(messageFrame.getRecipientAddress()).willReturn(recipient);
        given(messageFrame.getReturnData()).willReturn(returnData);
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getState()).willReturn(State.CODE_SUSPENDED);
        given(messageFrame.getDepth()).willReturn(1);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(output)
                .contains(
                        "type=MESSAGE_CALL",
                        "callDepth=0",
                        "recipient=0x3",
                        "input=0x696e70757444617461",
                        "operation=BALANCE",
                        "output=0x6f757470757444617461",
                        "remainingGas=1000",
                        "revertReason=",
                        "return=0x72657475726e44617461",
                        "sender=0x4")
                .contains(
                        "type=MESSAGE_CALL",
                        "callDepth=1",
                        "recipient=0x3",
                        "input=0x696e70757444617461",
                        "operation=BALANCE",
                        "output=0x6f757470757444617461",
                        "remainingGas=1000",
                        "return=0x72657475726e44617461",
                        "revertReason=",
                        "sender=0x4");
    }

    @Test
    void shouldSetBalanceCallFlagForBalanceOperation() {
        // Given
        final var balanceOperation = new AbstractOperation(0x31, BALANCE_OPERATION_NAME, 1, 1, null) {
            @Override
            public OperationResult execute(final MessageFrame frame, final org.hyperledger.besu.evm.EVM evm) {
                return new OperationResult(0L, null);
            }
        };
        given(messageFrame.getCurrentOperation()).willReturn(balanceOperation);

        // When
        mirrorOperationTracer.tracePreExecution(messageFrame);

        // Then
        verify(contractCallContext, times(1)).setBalanceCall(true);
    }

    @Test
    void shouldNotSetBalanceCallFlagForNonBalanceOperation() {
        // Given
        final var mulOperation = new AbstractOperation(0x02, "MUL", 2, 1, null) {
            @Override
            public OperationResult execute(final MessageFrame frame, final org.hyperledger.besu.evm.EVM evm) {
                return new OperationResult(0L, null);
            }
        };
        given(messageFrame.getCurrentOperation()).willReturn(mulOperation);

        // When
        mirrorOperationTracer.tracePreExecution(messageFrame);

        // Then
        verify(contractCallContext, never()).setBalanceCall(true);
    }

    @Test
    void shouldHandleNullCurrentOperation() {
        // Given
        given(messageFrame.getCurrentOperation()).willReturn(null);

        // When
        mirrorOperationTracer.tracePreExecution(messageFrame);

        // Then
        verify(contractCallContext, never()).setBalanceCall(true);
    }
}
