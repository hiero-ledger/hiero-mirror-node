// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.node.app.service.contract.impl.exec.utils.ActionStack.Validation.OFF;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.streams.CallOperationType;
import com.hedera.hapi.streams.ContractAction;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionWrapper;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.evm.properties.TraceProperties;
import org.hiero.mirror.web3.state.CommonEntityAccessor;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
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
    private static final Configuration CONFIGURATION = new EvmProperties().getVersionedConfiguration();
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Spy
    private ContractCallContext contractCallContext;

    @Mock
    private OperationResult operationResult;

    private TraceProperties traceProperties;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private MessageFrame childFrame;

    @Mock
    private ActionStack actionStack;

    @Mock
    private Code code;

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
        lenient().when(messageFrame.getState()).thenReturn(State.CODE_EXECUTING);
        lenient().doReturn(actionStack).when(contractCallContext).getActionStack();
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

    @Test
    void traceDataMeasuresFinalizedAction() {
        // Given
        final var action = givenOpenAction(32);
        given(messageFrame.getState()).willReturn(State.COMPLETED_SUCCESS);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        verify(actionStack).finalizeLastAction(messageFrame, OFF);
        assertThat(contractCallContext.getTraceDataBytes())
                .isEqualTo(ContractActions.PROTOBUF.measureRecord(new ContractActions(List.of(action))));
    }

    @Test
    void traceDataMeasuresCreatedBytecode() {
        // Given
        final var action = givenOpenAction(0);
        final var runtimeBytecode = Bytes.wrap(new byte[100]);
        final var initcode = Bytes.wrap(new byte[200]);
        given(messageFrame.getState()).willReturn(State.CODE_SUCCESS);
        given(messageFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(messageFrame.getDepth()).willReturn(1);
        given(messageFrame.getOutputData()).willReturn(runtimeBytecode);
        given(messageFrame.getCode()).willReturn(code);
        given(code.getBytes()).willReturn(initcode);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        final var bytecode = ContractBytecode.newBuilder()
                .runtimeBytecode(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(runtimeBytecode.toArrayUnsafe()))
                .initcode(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(initcode.toArrayUnsafe()))
                .build();
        assertThat(contractCallContext.getTraceDataBytes())
                .isEqualTo(ContractActions.PROTOBUF.measureRecord(new ContractActions(List.of(action)))
                        + ContractBytecode.PROTOBUF.measureRecord(bytecode));
    }

    @Test
    void traceDataMeasuresPrecompileAction() {
        // Given
        givenOpenAction(4);

        // When
        mirrorOperationTracer.tracePrecompileResult(messageFrame, ContractActionType.PRECOMPILE);

        // Then
        verify(actionStack).finalizeLastStackActionAsPrecompile(messageFrame, ContractActionType.PRECOMPILE, OFF);
        assertThat(contractCallContext.getTraceDataBytes()).isPositive();
    }

    @Test
    void traceDataTracksSuspendedAndNotExecutingFrames() {
        // Given
        givenOpenAction(childFrame, 0);

        // When
        mirrorOperationTracer.traceSuspended(messageFrame, childFrame, CallOperationType.OP_CALL);
        mirrorOperationTracer.traceNotExecuting(childFrame);

        // Then
        verify(actionStack).pushActionOfIntermediate(messageFrame, childFrame, CallOperationType.OP_CALL);
        verify(actionStack).finalizeLastAction(childFrame, OFF);
        assertThat(contractCallContext.getTraceDataBytes()).isPositive();
    }

    @Test
    void traceDataIgnoresFinalizationWithoutOpenAction() {
        // Given
        given(messageFrame.getState()).willReturn(State.COMPLETED_SUCCESS);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(contractCallContext.getTraceDataBytes()).isZero();
    }

    @Test
    void traceDataExceedingLimitFailsWithInsufficientGas() {
        // Given
        final var maxSerializedTraceDataBytes =
                CONFIGURATION.getConfigData(ContractsConfig.class).maxSerializedTraceDataBytes();
        givenOpenAction(maxSerializedTraceDataBytes);
        given(messageFrame.getState()).willReturn(State.COMPLETED_SUCCESS);

        // When / Then
        assertThatThrownBy(() -> mirrorOperationTracer.tracePostExecution(messageFrame, operationResult))
                .isInstanceOfSatisfying(
                        HandleException.class, e -> assertThat(e.getStatus()).isEqualTo(INSUFFICIENT_GAS));
    }

    // The mocked action stack leaves the open action as is, so it is measured as given
    private ContractAction givenOpenAction(final int outputSize) {
        return givenOpenAction(messageFrame, outputSize);
    }

    private ContractAction givenOpenAction(final MessageFrame frame, final int outputSize) {
        final var action = ContractAction.newBuilder()
                .callDepth(1)
                .gas(INITIAL_GAS)
                .output(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(new byte[outputSize]))
                .build();
        contractCallContext.getOpenActions().push(new ActionWrapper(action));
        given(frame.getMessageFrameStack()).willReturn(new ArrayDeque<>());
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(CONFIGURATION);
        return action;
    }
}
