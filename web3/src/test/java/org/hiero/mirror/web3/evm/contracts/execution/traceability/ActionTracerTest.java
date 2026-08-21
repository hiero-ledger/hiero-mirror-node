// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.service.contract.impl.utils.OpcodeUtils;
import java.util.Deque;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.viewmodel.TracerConfig;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractOperation;
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

@ExtendWith(MockitoExtension.class)
class ActionTracerTest {

    private static final Address RECIPIENT = Address.fromHexString("0x3");
    private static final Address SENDER = Address.fromHexString("0x4");
    private static final Address NESTED_RECIPIENT = Address.fromHexString("0x5");
    private static final Address NESTED_SENDER = Address.fromHexString("0x3");
    private static final long INITIAL_GAS = 1000L;
    private static final long REMAINING_GAS = 900L;
    private static final long NESTED_GAS = 500L;
    private static final long NESTED_REMAINING_GAS = 400L;
    private static final Bytes INPUT = Bytes.of("inputData".getBytes());
    private static final Bytes OUTPUT = Bytes.of("outputData".getBytes());
    private static final Bytes NESTED_INPUT = Bytes.of("nestedInput".getBytes());
    private static final Bytes NESTED_OUTPUT = Bytes.of("nestedOutput".getBytes());
    private static final Operation CALL_OPERATION =
            new AbstractOperation(OpcodeUtils.OP_CODE_CALL, "CALL", 7, 1, null) {
                @Override
                public OperationResult execute(final MessageFrame frame, final EVM evm) {
                    return new OperationResult(0, null);
                }
            };

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Spy
    private ContractCallContext contractCallContext;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private MessageFrame nestedFrame;

    @Mock
    private OperationResult operationResult;

    @Mock
    private Deque<MessageFrame> messageFrameStack;

    private ActionTracer actionTracer;
    private ActionContext actionContext;

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
        actionTracer = new ActionTracer();
        actionContext = ActionContext.builder()
                .tracerConfig(TracerConfig.builder().build())
                .gasRemaining(INITIAL_GAS)
                .build();
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
        lenient().when(contractCallContext.getActionContext()).thenReturn(actionContext);
    }

    @Test
    void traceContextEnterUpdatesGasRemaining() {
        // Given
        given(messageFrame.getRemainingGas()).willReturn(REMAINING_GAS);

        // When
        actionTracer.traceContextEnter(messageFrame);

        // Then
        assertThat(actionContext.getGasRemaining()).isEqualTo(REMAINING_GAS);
    }

    @Test
    void traceContextReEnterUpdatesGasRemaining() {
        // Given
        given(messageFrame.getRemainingGas()).willReturn(REMAINING_GAS);

        // When
        actionTracer.traceContextReEnter(messageFrame);

        // Then
        assertThat(actionContext.getGasRemaining()).isEqualTo(REMAINING_GAS);
    }

    @Test
    void traceOriginActionRecordsTopLevelAction() {
        // Given
        givenOriginFrameData();

        // When
        actionTracer.traceOriginAction(messageFrame);

        // Then
        assertThat(actionContext.getGasRemaining()).isEqualTo(INITIAL_GAS);
        assertThat(actionContext.getActions()).hasSize(1);
        assertTopLevelAction(actionContext.getActions().getFirst(), false);
    }

    @Test
    void doesNotRecordActionWhileCodeExecuting() {
        // Given
        given(messageFrame.getState()).willReturn(CODE_EXECUTING);

        // When
        actionTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(actionContext.getActions()).isEmpty();
    }

    @Test
    void recordsNestedActionUnderParentCallsWhenCodeSuspended() {
        // Given
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);
        givenSuspendedParentWithChild();

        // When
        actionTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(actionContext.getActions()).hasSize(1);
        final var topLevel = actionContext.getActions().getFirst();
        assertThat(topLevel.getCalls()).hasSize(1);
        assertNestedAction(topLevel.getCalls().getFirst(), false);
    }

    @Test
    void skipsNestedActionWhenOnlyTopCallEnabled() {
        // Given
        actionContext.setTracerConfig(TracerConfig.builder().onlyTopCall(true).build());
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);
        given(messageFrame.getState()).willReturn(CODE_SUSPENDED);

        // When
        actionTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(actionContext.getActions()).hasSize(1);
        assertThat(actionContext.getActions().getFirst().getCalls()).isNullOrEmpty();
    }

    @Test
    void finalizesTopLevelActionOnCompletedFrame() {
        // Given
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);
        givenCompletedFrameData(messageFrame, REMAINING_GAS, OUTPUT);

        // When
        actionTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(actionContext.getActions()).hasSize(1);
        assertTopLevelAction(actionContext.getActions().getFirst(), true);
    }

    @Test
    void finalizesNestedActionAndKeepsItUnderParentCalls() {
        // Given
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);
        givenSuspendedParentWithChild();
        actionTracer.tracePostExecution(messageFrame, operationResult);

        actionContext.setGasRemaining(NESTED_GAS);
        givenCompletedFrameData(nestedFrame, NESTED_REMAINING_GAS, NESTED_OUTPUT);
        given(nestedFrame.getDepth()).willReturn(1);

        // When
        actionTracer.tracePostExecution(nestedFrame, operationResult);

        // Then
        assertThat(actionContext.getActions()).hasSize(1);
        final var topLevel = actionContext.getActions().getFirst();
        assertThat(topLevel.getCalls()).hasSize(1);
        assertNestedAction(topLevel.getCalls().getFirst(), true);
    }

    @Test
    void nestsSiblingCallsUnderSameParent() {
        // Given
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);

        givenSuspendedParentWithChild();
        actionTracer.tracePostExecution(messageFrame, operationResult);
        actionContext.setGasRemaining(NESTED_GAS);
        givenCompletedFrameData(nestedFrame, NESTED_REMAINING_GAS, NESTED_OUTPUT);
        given(nestedFrame.getDepth()).willReturn(1);
        actionTracer.tracePostExecution(nestedFrame, operationResult);

        final var siblingFrame = mock(MessageFrame.class);
        given(messageFrame.getState()).willReturn(CODE_SUSPENDED);
        given(messageFrame.getCurrentOperation()).willReturn(CALL_OPERATION);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrameStack.peek()).willReturn(siblingFrame);
        given(siblingFrame.getDepth()).willReturn(1);
        given(siblingFrame.getSenderAddress()).willReturn(NESTED_SENDER);
        given(siblingFrame.getRecipientAddress()).willReturn(Address.fromHexString("0x6"));
        given(siblingFrame.getRemainingGas()).willReturn(NESTED_GAS);
        given(siblingFrame.getInputData()).willReturn(NESTED_INPUT);

        // When
        actionTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        final var topLevel = actionContext.getActions().getFirst();
        assertThat(topLevel.getCalls()).hasSize(2);
        assertThat(topLevel.getCalls().get(0).getTo()).isEqualTo(NESTED_RECIPIENT.toHexString());
        assertThat(topLevel.getCalls().get(1).getTo())
                .isEqualTo(Address.fromHexString("0x6").toHexString());
    }

    @Test
    void nestsDeepCallsByDepth() {
        // Given
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);

        // depth 0 -> depth 1
        givenSuspendedParentWithChild();
        actionTracer.tracePostExecution(messageFrame, operationResult);

        // depth 1 -> depth 2
        final var depth2Frame = mock(MessageFrame.class);
        @SuppressWarnings("unchecked")
        final Deque<MessageFrame> nestedStack = mock(Deque.class);
        given(nestedFrame.getState()).willReturn(CODE_SUSPENDED);
        given(nestedFrame.getCurrentOperation()).willReturn(CALL_OPERATION);
        given(nestedFrame.getMessageFrameStack()).willReturn(nestedStack);
        given(nestedStack.peek()).willReturn(depth2Frame);
        given(depth2Frame.getDepth()).willReturn(2);
        given(depth2Frame.getSenderAddress()).willReturn(NESTED_RECIPIENT);
        given(depth2Frame.getRecipientAddress()).willReturn(Address.fromHexString("0x7"));
        given(depth2Frame.getRemainingGas()).willReturn(200L);
        given(depth2Frame.getInputData()).willReturn(Bytes.of("deep".getBytes()));

        // When
        actionTracer.tracePostExecution(nestedFrame, operationResult);

        // Then
        final var topLevel = actionContext.getActions().getFirst();
        assertThat(topLevel.getCalls()).hasSize(1);
        final var depth1 = topLevel.getCalls().getFirst();
        assertThat(depth1.getCalls()).hasSize(1);
        assertThat(depth1.getCalls().getFirst().getTo())
                .isEqualTo(Address.fromHexString("0x7").toHexString());
        assertThat(depth1.getCalls().getFirst().getType()).isEqualTo(TypeEnum.CALL);
    }

    @Test
    void recordsCompletedFrameEvenWhenOnlyTopCallEnabled() {
        // Given
        actionContext.setTracerConfig(TracerConfig.builder().onlyTopCall(true).build());
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);
        givenCompletedFrameData(messageFrame, REMAINING_GAS, OUTPUT);

        // When
        actionTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(actionContext.getActions()).hasSize(1);
        assertTopLevelAction(actionContext.getActions().getFirst(), true);
        assertThat(actionContext.getActions().getFirst().getCalls()).isNullOrEmpty();
    }

    @Test
    void doesNotFinalizeNestedFrameWhenOnlyTopCallEnabled() {
        // Given
        actionContext.setTracerConfig(TracerConfig.builder().onlyTopCall(true).build());
        givenOriginFrameData();
        actionTracer.traceOriginAction(messageFrame);
        given(nestedFrame.getState()).willReturn(COMPLETED_SUCCESS);
        given(nestedFrame.getDepth()).willReturn(1);

        // When
        actionTracer.tracePostExecution(nestedFrame, operationResult);

        // Then
        assertThat(actionContext.getActions()).hasSize(1);
        assertThat(actionContext.getActions().getFirst().getCalls()).isNullOrEmpty();
    }

    @Test
    void contractActionsIsEmpty() {
        // When
        final var result = actionTracer.contractActions();

        // Then
        assertThat(result).isEmpty();
    }

    private void givenOriginFrameData() {
        given(messageFrame.getRemainingGas()).willReturn(INITIAL_GAS);
        given(messageFrame.getDepth()).willReturn(0);
        given(messageFrame.getType()).willReturn(MESSAGE_CALL);
        given(messageFrame.getSenderAddress()).willReturn(SENDER);
        given(messageFrame.getRecipientAddress()).willReturn(RECIPIENT);
        given(messageFrame.getInputData()).willReturn(INPUT);
    }

    private void givenSuspendedParentWithChild() {
        given(messageFrame.getState()).willReturn(CODE_SUSPENDED);
        given(messageFrame.getCurrentOperation()).willReturn(CALL_OPERATION);
        given(messageFrame.getMessageFrameStack()).willReturn(messageFrameStack);
        given(messageFrameStack.peek()).willReturn(nestedFrame);
        given(nestedFrame.getDepth()).willReturn(1);
        given(nestedFrame.getSenderAddress()).willReturn(NESTED_SENDER);
        given(nestedFrame.getRecipientAddress()).willReturn(NESTED_RECIPIENT);
        given(nestedFrame.getRemainingGas()).willReturn(NESTED_GAS);
        given(nestedFrame.getInputData()).willReturn(NESTED_INPUT);
    }

    private void givenCompletedFrameData(final MessageFrame frame, final long remainingGas, final Bytes output) {
        given(frame.getState()).willReturn(COMPLETED_SUCCESS);
        given(frame.getExceptionalHaltReason()).willReturn(Optional.empty());
        given(frame.getRemainingGas()).willReturn(remainingGas);
        given(frame.getOutputData()).willReturn(output);
        given(frame.getRevertReason()).willReturn(Optional.empty());
    }

    private void assertTopLevelAction(final ActionResponse action, final boolean finalized) {
        assertThat(action.getFrom()).isEqualTo(SENDER.toHexString());
        assertThat(action.getTo()).isEqualTo(RECIPIENT.toHexString());
        assertThat(action.getInput()).isEqualTo(INPUT.toHexString());
        assertThat(action.getType()).isEqualTo(TypeEnum.CALL);
        assertThat(action.getGas())
                .isEqualTo(Bytes.wrap(String.valueOf(INITIAL_GAS).getBytes()).toHexString());
        if (finalized) {
            assertThat(action.getOutput()).isEqualTo(OUTPUT.toHexString());
            assertThat(action.getError()).isEqualTo(ExceptionalHaltReason.NONE.toString());
            assertThat(action.getRevertReason()).isEqualTo(Bytes.EMPTY.toHexString());
            assertThat(action.getGasUsed())
                    .isEqualTo(Bytes.wrap(
                                    String.valueOf(INITIAL_GAS - REMAINING_GAS).getBytes())
                            .toHexString());
        }
    }

    private void assertNestedAction(final ActionResponse action, final boolean finalized) {
        assertThat(action.getFrom()).isEqualTo(NESTED_SENDER.toHexString());
        assertThat(action.getTo()).isEqualTo(NESTED_RECIPIENT.toHexString());
        assertThat(action.getInput()).isEqualTo(NESTED_INPUT.toHexString());
        assertThat(action.getType()).isEqualTo(TypeEnum.CALL);
        assertThat(action.getGas())
                .isEqualTo(Bytes.wrap(String.valueOf(NESTED_GAS).getBytes()).toHexString());
        if (finalized) {
            assertThat(action.getOutput()).isEqualTo(NESTED_OUTPUT.toHexString());
            assertThat(action.getError()).isEqualTo(ExceptionalHaltReason.NONE.toString());
            assertThat(action.getRevertReason()).isEqualTo(Bytes.EMPTY.toHexString());
            assertThat(action.getGasUsed())
                    .isEqualTo(Bytes.wrap(String.valueOf(NESTED_GAS - NESTED_REMAINING_GAS)
                                    .getBytes())
                            .toHexString());
        }
    }
}
