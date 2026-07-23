// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_SUSPENDED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.service.contract.impl.utils.OpcodeUtils;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
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
    private static final long INITIAL_GAS = 1000L;
    private static final long REMAINING_GAS = 900L;
    private static final Bytes INPUT = Bytes.of("inputData".getBytes());
    private static final Bytes OUTPUT = Bytes.of("outputData".getBytes());
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
    private OperationResult operationResult;

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
        given(messageFrame.getRemainingGas()).willReturn(REMAINING_GAS);

        actionTracer.traceContextEnter(messageFrame);

        assertThat(actionContext.getGasRemaining()).isEqualTo(REMAINING_GAS);
    }

    @Test
    void traceContextReEnterUpdatesGasRemaining() {
        given(messageFrame.getRemainingGas()).willReturn(REMAINING_GAS);

        actionTracer.traceContextReEnter(messageFrame);

        assertThat(actionContext.getGasRemaining()).isEqualTo(REMAINING_GAS);
    }

    @Test
    void traceOriginActionUpdatesGasRemaining() {
        given(messageFrame.getRemainingGas()).willReturn(REMAINING_GAS);

        actionTracer.traceOriginAction(messageFrame);

        assertThat(actionContext.getGasRemaining()).isEqualTo(REMAINING_GAS);
    }

    @Test
    void doesNotRecordActionWhileCodeExecuting() {
        given(messageFrame.getState()).willReturn(CODE_EXECUTING);

        actionTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(actionContext.getActions()).isEmpty();
    }

    @Test
    void recordsNestedActionWhenCodeSuspendedAndOnlyTopCallDisabled() {
        givenFrameData(CODE_SUSPENDED);

        actionTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(actionContext.getActions()).hasSize(1);
        assertAction(actionContext.getActions().getFirst());
    }

    @Test
    void skipsNestedActionWhenOnlyTopCallEnabled() {
        actionContext.setTracerConfig(TracerConfig.builder().onlyTopCall(true).build());
        given(messageFrame.getState()).willReturn(CODE_SUSPENDED);

        actionTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(actionContext.getActions()).isEmpty();
    }

    @Test
    void recordsCompletedFrameAction() {
        givenFrameData(COMPLETED_SUCCESS);

        actionTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(actionContext.getActions()).hasSize(1);
        assertAction(actionContext.getActions().getFirst());
    }

    @Test
    void recordsCompletedFrameEvenWhenOnlyTopCallEnabled() {
        actionContext.setTracerConfig(TracerConfig.builder().onlyTopCall(true).build());
        givenFrameData(COMPLETED_SUCCESS);

        actionTracer.tracePostExecution(messageFrame, operationResult);

        assertThat(actionContext.getActions()).hasSize(1);
        assertAction(actionContext.getActions().getFirst());
    }

    @Test
    void contractActionsIsEmpty() {
        assertThat(actionTracer.contractActions()).isEmpty();
    }

    private void givenFrameData(final MessageFrame.State state) {
        given(messageFrame.getState()).willReturn(state);
        given(messageFrame.getExceptionalHaltReason()).willReturn(Optional.empty());
        given(messageFrame.getSenderAddress()).willReturn(SENDER);
        given(messageFrame.getRemainingGas()).willReturn(REMAINING_GAS);
        given(messageFrame.getInputData()).willReturn(INPUT);
        given(messageFrame.getOutputData()).willReturn(OUTPUT);
        given(messageFrame.getRevertReason()).willReturn(Optional.empty());
        given(messageFrame.getRecipientAddress()).willReturn(RECIPIENT);
        given(messageFrame.getCurrentOperation()).willReturn(CALL_OPERATION);
    }

    private void assertAction(final org.hiero.mirror.rest.model.ActionResponse action) {
        assertThat(action.getFrom()).isEqualTo(SENDER.toHexString());
        assertThat(action.getTo()).isEqualTo(RECIPIENT.toHexString());
        assertThat(action.getInput()).isEqualTo(INPUT.toHexString());
        assertThat(action.getOutput()).isEqualTo(OUTPUT.toHexString());
        assertThat(action.getError()).isEqualTo(ExceptionalHaltReason.NONE.toString());
        assertThat(action.getRevertReason()).isEqualTo(Bytes.EMPTY.toHexString());
        assertThat(action.getType()).isEqualTo(TypeEnum.CALL);
        assertThat(action.getGas())
                .isEqualTo(Bytes.wrap(String.valueOf(REMAINING_GAS).getBytes()).toHexString());
        assertThat(action.getGasUsed())
                .isEqualTo(
                        Bytes.wrap(String.valueOf(INITIAL_GAS - REMAINING_GAS).getBytes())
                                .toHexString());
    }
}
