// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.streams.ContractActionType;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
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
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private ProxyWorldUpdater worldUpdater;

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
        final var messageFrameStack = new ArrayDeque<>(List.of(childFrame));
        lenient().when(messageFrame.getMessageFrameStack()).thenReturn(messageFrameStack);
        lenient().when(childFrame.getMessageFrameStack()).thenReturn(messageFrameStack);
        lenient().when(childFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).thenReturn(CONFIGURATION);
        lenient().when(childFrame.getInputData()).thenReturn(Bytes.EMPTY);
        lenient().when(childFrame.getSenderAddress()).thenReturn(sender);
        lenient().when(childFrame.getContractAddress()).thenReturn(contract);
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

    @ParameterizedTest
    @ValueSource(ints = {0, 32})
    void traceDataMatchesSerializedAction(final int outputSize) {
        // Given
        final var input = Bytes.of(1, 2, 3, 4);
        final var output = Bytes.wrap(new byte[outputSize]);
        final var template = AbstractActionTracer.ACTION_TEMPLATE;
        final var action = template.copyBuilder()
                .callingContract(template.callingContractOrThrow().copyBuilder().contractNum(numberOfLongZero(sender)))
                .recipientContract(
                        template.recipientContractOrThrow().copyBuilder().contractNum(numberOfLongZero(contract)))
                .input(pbjBytes(input))
                .output(pbjBytes(output))
                .callDepth(0)
                .build();
        given(messageFrame.getSenderAddress()).willReturn(sender);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getInputData()).willReturn(input);
        given(messageFrame.getOutputData()).willReturn(output);

        // When
        mirrorOperationTracer.traceOriginAction(messageFrame);
        given(messageFrame.getState()).willReturn(State.COMPLETED_SUCCESS);
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(contractCallContext.getTraceDataBytes())
                .isEqualTo(new ContractActions(List.of(action)).protobufSize());
    }

    @Test
    void traceDataResolvesAliasNumber() {
        // Given
        final var alias = Address.fromHexString("0x7a3f5c0b1e2d4a6f8b9c0d1e2f3a4b5c6d7e8f90");
        final var aliasNum = 1_000_000L;
        final var template = AbstractActionTracer.ACTION_TEMPLATE;
        final var action = template.copyBuilder()
                .callingContract(template.callingContractOrThrow().copyBuilder().contractNum(aliasNum))
                .recipientContract(
                        template.recipientContractOrThrow().copyBuilder().contractNum(numberOfLongZero(contract)))
                .callDepth(0)
                .build();
        final var account = mock(HederaEvmAccount.class);
        given(account.hederaId())
                .willReturn(AccountID.newBuilder().accountNum(aliasNum).build());
        given(worldUpdater.getHederaAccount(alias)).willReturn(account);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(messageFrame.getSenderAddress()).willReturn(alias);
        given(messageFrame.getContractAddress()).willReturn(contract);
        given(messageFrame.getInputData()).willReturn(Bytes.EMPTY);

        // When
        mirrorOperationTracer.traceOriginAction(messageFrame);

        // Then
        assertThat(contractCallContext.getTraceDataBytes())
                .isEqualTo(new ContractActions(List.of(action)).protobufSize());
    }

    @Test
    void traceDataBytesCountsSuccessfulChildCreationInitcode() {
        // Given
        final var code = mock(Code.class);
        given(code.getSize()).willReturn(200);
        given(messageFrame.getState()).willReturn(State.CODE_SUCCESS);
        given(messageFrame.getType()).willReturn(Type.CONTRACT_CREATION);
        given(messageFrame.getDepth()).willReturn(1);
        given(messageFrame.getCode()).willReturn(code);
        given(messageFrame.getOutputData()).willReturn(Bytes.EMPTY);

        // When
        mirrorOperationTracer.tracePostExecution(messageFrame, operationResult);

        // Then
        assertThat(contractCallContext.getTraceDataBytes()).isEqualTo(2 + (3 + 200));
    }

    @Test
    void traceDataBytesCountsPrecompileRevertReason() {
        // Given
        given(messageFrame.getState()).willReturn(State.REVERT);
        given(messageFrame.getRevertReason()).willReturn(Optional.of(Bytes.of(1, 2, 3, 4)));

        // When
        mirrorOperationTracer.tracePrecompileResult(messageFrame, ContractActionType.PRECOMPILE);

        // Then
        assertThat(contractCallContext.getTraceDataBytes()).isEqualTo(2 + 4);
    }

    @Test
    void traceDataBytesCountsHaltReason() {
        // Given
        given(messageFrame.getState()).willReturn(State.EXCEPTIONAL_HALT);
        given(messageFrame.getExceptionalHaltReason()).willReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

        // When
        mirrorOperationTracer.tracePrecompileResult(messageFrame, ContractActionType.PRECOMPILE);

        // Then
        assertThat(contractCallContext.getTraceDataBytes())
                .isEqualTo(2 + ExceptionalHaltReason.INSUFFICIENT_GAS.name().length());
    }

    @Test
    void traceDataExceedingLimitFailsWithInsufficientGas() {
        // Given
        final int maxSerializedTraceDataBytes =
                CONFIGURATION.getConfigData(ContractsConfig.class).maxSerializedTraceDataBytes();
        given(messageFrame.getState()).willReturn(State.COMPLETED_SUCCESS);
        given(messageFrame.getOutputData()).willReturn(Bytes.wrap(new byte[maxSerializedTraceDataBytes + 1]));

        // When / Then
        assertThatThrownBy(() -> mirrorOperationTracer.tracePostExecution(messageFrame, operationResult))
                .isInstanceOfSatisfying(
                        HandleException.class, e -> assertThat(e.getStatus()).isEqualTo(INSUFFICIENT_GAS));
    }

    private static com.hedera.pbj.runtime.io.buffer.Bytes pbjBytes(final Bytes bytes) {
        return com.hedera.pbj.runtime.io.buffer.Bytes.wrap(bytes.toArrayUnsafe());
    }
}
