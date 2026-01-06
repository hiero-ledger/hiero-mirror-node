// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.convert.BytesDecoder.getAbiEncodedRevertReason;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_FAILED;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.NOT_STARTED;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.CONTRACT_CREATION;
import static org.hyperledger.besu.evm.frame.MessageFrame.Type.MESSAGE_CALL;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSortedMap;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.state.EvmFrameState;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.StorageAccess;
import com.hedera.node.app.service.contract.impl.state.StorageAccesses;
import com.hedera.node.app.service.contract.impl.state.TxStorageUsage;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractActionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import org.apache.commons.codec.binary.Hex;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.assertj.core.util.Lists;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.ModificationNotAllowedException;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@CustomLog
@DisplayName("OpcodeActionTracer")
@ExtendWith(MockitoExtension.class)
class OpcodeActionTracerTest {

    private static final Address CONTRACT_ADDRESS = Address.fromHexString("0x123");
    private static final Address ETH_PRECOMPILE_ADDRESS = Address.fromHexString("0x01");
    private static final Address HTS_PRECOMPILE_ADDRESS = Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS);
    private static final long INITIAL_GAS = 1000L;
    private static final long GAS_COST = 2L;
    private static final long GAS_PRICE = 200L;
    private static final long GAS_REQUIREMENT = 100L;
    private static final Bytes DEFAULT_OUTPUT = Bytes.fromHexString("0x1234567890abcdef");
    private static final AtomicReference<Long> REMAINING_GAS = new AtomicReference<>();
    private static final AtomicReference<Integer> EXECUTED_FRAMES = new AtomicReference<>(0);
    private static final Operation OPERATION = new AbstractOperation(0x02, "MUL", 2, 1, null) {
        @Override
        public OperationResult execute(final MessageFrame frame, final EVM evm) {
            return new OperationResult(GAS_COST, null);
        }
    };
    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Spy
    private ContractCallContext contractCallContext;

    @Mock
    private ProxyWorldUpdater worldUpdater;

    @Mock
    private RootProxyWorldUpdater rootProxyWorldUpdater;

    @Mock
    private EvmFrameState evmFrameState;

    @Mock
    private MutableAccount recipientAccount;

    @Mock
    private MirrorNodeEvmProperties mirrorNodeEvmProperties;

    // Transient test data
    private OpcodeActionTracer tracer;
    private OpcodeTracerOptions tracerOptions;
    private MessageFrame frame;

    // EVM data for capture
    private UInt256[] stackItems;
    private Bytes[] wordsInMemory;
    private Map<UInt256, UInt256> updatedStorage;
    private TxStorageUsage txStorageUsage;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setUp() {
        REMAINING_GAS.set(INITIAL_GAS);
        tracer = new OpcodeActionTracer();
        tracer.setSystemContracts(Map.of(HTS_PRECOMPILE_ADDRESS, mock(HederaSystemContract.class)));
        tracerOptions = new OpcodeTracerOptions(false, false, false);
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @AfterEach
    void tearDown() {
        verifyMocks();
        reset(contractCallContext);
        reset(worldUpdater);
        reset(recipientAccount);
    }

    private void verifyMocks() {
        if (tracerOptions.isStorage()) {
            try {
                MutableAccount account = worldUpdater.getAccount(frame.getRecipientAddress());
                if (account != null) {
                    assertThat(account).isEqualTo(recipientAccount);
                }
            } catch (final ModificationNotAllowedException e) {
            }
        } else {
            verify(worldUpdater, never()).getAccount(any());
        }
    }

    @Test
    @DisplayName("should record program counter")
    void shouldRecordProgramCounter() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.pc()).isEqualTo(frame.getPC());
    }

    @Test
    @DisplayName("should record opcode")
    void shouldRecordOpcode() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.op()).isNotEmpty();
        assertThat(opcode.op()).contains(OPERATION.getName());
    }

    @Test
    @DisplayName("should record depth")
    void shouldRecordDepth() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // simulate 4 calls
        final int expectedDepth = 4;
        for (int i = 0; i < expectedDepth; i++) {
            frame.getMessageFrameStack()
                    .add(buildMessageFrame(Address.fromHexString("0x10%d".formatted(i)), MESSAGE_CALL));
        }

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.depth()).isEqualTo(expectedDepth);
    }

    @Test
    @DisplayName("should record remaining gas")
    void shouldRecordRemainingGas() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.gas()).isEqualTo(REMAINING_GAS.get());
    }

    @Test
    @DisplayName("should record gas cost")
    void shouldRecordGasCost() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.gasCost()).isEqualTo(GAS_COST);
    }

    @Test
    @DisplayName("given stack is enabled in tracer options, should record stack")
    void shouldRecordStackWhenEnabled() {
        // Given
        tracerOptions = tracerOptions.toBuilder().stack(true).build();
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.stack()).isNotEmpty();
        assertThat(opcode.stack()).containsExactly(stackItems);
    }

    @Test
    @DisplayName("given stack is disabled in tracer options, should not record stack")
    void shouldNotRecordStackWhenDisabled() {
        // Given
        tracerOptions = tracerOptions.toBuilder().stack(false).build();
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.stack()).isEmpty();
    }

    @Test
    @DisplayName("given memory is enabled in tracer options, should record memory")
    void shouldRecordMemoryWhenEnabled() {
        // Given
        tracerOptions = tracerOptions.toBuilder().memory(true).build();
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.memory()).isNotEmpty();
        assertThat(opcode.memory()).containsExactly(wordsInMemory);
    }

    @Test
    @DisplayName("given memory is disabled in tracer options, should not record memory")
    void shouldNotRecordMemoryWhenDisabled() {
        // Given
        tracerOptions = tracerOptions.toBuilder().memory(false).build();
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.memory()).isEmpty();
    }

    @Test
    @DisplayName("given storage is enabled in tracer options, should record storage")
    void shouldRecordStorage() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupInitialFrame(tracerOptions);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(txStorageUsage);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isNotEmpty().containsAllEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName(
            "given storage is enabled in tracer options, should return empty storage when there are no updates for modularized services")
    void shouldReturnEmptyStorageWhenThereAreNoUpdates() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupInitialFrame(tracerOptions);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(new TxStorageUsage(List.of(), Set.of()));

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName("given account is missing in the world updater, should only log a warning and return empty storage")
    void shouldNotThrowExceptionWhenAccountIsMissingInWorldUpdater() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(true).build();
        frame = setupInitialFrame(tracerOptions);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(new TxStorageUsage(List.of(), Set.of()));

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).containsExactlyEntriesOf(new TreeMap<>());
    }

    @Test
    @DisplayName("given storage is disabled in tracer options, should not record storage")
    void shouldNotRecordStorageWhenDisabled() {
        // Given
        tracerOptions = tracerOptions.toBuilder().storage(false).build();
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.storage()).isEmpty();
    }

    @Test
    @DisplayName("given exceptional halt occurs, should capture frame data and halt reason")
    void shouldCaptureFrameWhenExceptionalHaltOccurs() {
        // Given
        tracerOptions =
                tracerOptions.toBuilder().stack(true).memory(true).storage(true).build();
        frame = setupInitialFrame(tracerOptions);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(txStorageUsage);

        // When
        final Opcode opcode = executeOperation(frame, INSUFFICIENT_GAS);

        // Then
        assertThat(opcode.reason())
                .contains(Hex.encodeHexString(INSUFFICIENT_GAS.getDescription().getBytes()));
        assertThat(opcode.stack()).contains(stackItems);
        assertThat(opcode.memory()).contains(wordsInMemory);
        assertThat(opcode.storage()).containsExactlyEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName("should capture a precompile call")
    void shouldCaptureFrameWhenSuccessfulPrecompileCallOccurs() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcode.pc()).isEqualTo(frame.getPC());
        assertThat(opcode.op()).isNotEmpty().isEqualTo(OPERATION.getName());
        assertThat(opcode.gas()).isEqualTo(REMAINING_GAS.get());
        assertThat(opcode.gasCost()).isEqualTo(GAS_REQUIREMENT);
        assertThat(opcode.depth()).isEqualTo(frame.getDepth());
        assertThat(opcode.stack()).isEmpty();
        assertThat(opcode.memory()).isEmpty();
        assertThat(opcode.storage()).isEmpty();
        assertThat(opcode.reason())
                .isEqualTo(frame.getRevertReason().map(Bytes::toString).orElse(null));
    }

    @Test
    @DisplayName("should not record gas requirement of precompile call with null output")
    void shouldNotRecordGasRequirementWhenPrecompileCallHasNullOutput() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executePrecompileOperation(frame, GAS_REQUIREMENT, Bytes.EMPTY);

        // Then
        assertThat(opcode.gasCost()).isZero();
    }

    @Test
    @DisplayName("should not record revert reason of precompile call with no revert reason")
    void shouldNotRecordRevertReasonWhenPrecompileCallHasNoRevertReason() {
        // Given
        frame = setupInitialFrame(tracerOptions);

        // When
        final Opcode opcode = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcode.reason()).isNull();
    }

    @Test
    @DisplayName("should record revert reason of precompile call when frame has revert reason")
    void shouldRecordRevertReasonWhenEthPrecompileCallHasRevertReason() {
        // Given
        frame = setupInitialFrame(tracerOptions, ETH_PRECOMPILE_ADDRESS, MESSAGE_CALL);
        frame.setRevertReason(Bytes.of("revert reason".getBytes()));

        // When
        final Opcode opcode = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcode.reason())
                .isNotEmpty()
                .isEqualTo(frame.getRevertReason().map(Bytes::toString).orElseThrow());
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with plaintext revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithPlaintextRevertReason() {
        // Given
        final var contractActionNoRevert = getContractActionNoRevert();
        final var contractActionWithRevert = getContractActionWithRevert();
        contractActionWithRevert.setResultData("revert reason".getBytes());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.reason())
                .isNotEmpty()
                .isEqualTo(getAbiEncodedRevertReason(Bytes.of(contractActionWithRevert.getResultData()))
                        .toHexString());
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with response code for revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithResponseCodeNumberRevertReason() {
        // Given
        final var contractActionNoRevert = getContractActionNoRevert();
        final var contractActionWithRevert = getContractActionWithRevert();
        contractActionWithRevert.setResultData(ByteBuffer.allocate(32)
                .putInt(28, ResponseCodeEnum.INVALID_ACCOUNT_ID.getNumber())
                .array());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.reason())
                .isNotEmpty()
                .isEqualTo(getAbiEncodedRevertReason(Bytes.of(
                                ResponseCodeEnum.INVALID_ACCOUNT_ID.name().getBytes()))
                        .toHexString());
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with ABI-encoded revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithAbiEncodedRevertReason() {
        // Given
        final var contractActionNoRevert = getContractActionNoRevert();
        final var contractActionWithRevert =
                contractAction(1, 1, CallOperationType.OP_CALL, REVERT_REASON.getNumber(), HTS_PRECOMPILE_ADDRESS);
        contractActionWithRevert.setResultData(
                getAbiEncodedRevertReason(Bytes.of(INVALID_OPERATION.name().getBytes()))
                        .toArray());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.reason())
                .isNotEmpty()
                .isEqualTo(Bytes.of(contractActionWithRevert.getResultData()).toHexString());
    }

    @Test
    @DisplayName("should return empty revert reason of precompile call with empty revert reason")
    void shouldReturnEmptyReasonWhenPrecompileCallHasContractActionWithEmptyRevertReason() {
        // Given
        final var contractActionNoRevert = getContractActionNoRevert();
        final var contractActionWithRevert = getContractActionWithRevert();
        contractActionWithRevert.setResultData(Bytes.EMPTY.toArray());

        frame = setupInitialFrame(
                tracerOptions, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.reason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.reason()).isNotNull().isEqualTo(Bytes.EMPTY.toHexString());
    }

    @Test
    @DisplayName("should set balance call flag when BALANCE opcode is executed")
    void shouldSetBalanceCallFlagForBalanceOperation() {
        // Given
        final var balanceOperation = new AbstractOperation(0x31, "BALANCE", 1, 1, null) {
            @Override
            public OperationResult execute(final MessageFrame frame, final EVM evm) {
                return new OperationResult(GAS_COST, null);
            }
        };
        frame = setupInitialFrame(tracerOptions);
        frame.setCurrentOperation(balanceOperation);

        // When
        tracer.tracePreExecution(frame);

        // Then
        verify(contractCallContext, times(1)).setBalanceCall(true);
    }

    @Test
    @DisplayName("should not set balance call flag when non-BALANCE opcode is executed")
    void shouldNotSetBalanceCallFlagForNonBalanceOperation() {
        // Given
        frame = setupInitialFrame(tracerOptions);
        frame.setCurrentOperation(OPERATION); // MUL operation

        // When
        tracer.tracePreExecution(frame);

        // Then
        verify(contractCallContext, never()).setBalanceCall(true);
    }

    @Test
    @DisplayName("should not throw exception when current operation is null")
    void shouldHandleNullCurrentOperation() {
        // Given
        frame = setupInitialFrame(tracerOptions);
        frame.setCurrentOperation(null);

        // When & Then
        tracer.tracePreExecution(frame);

        // Then
        verify(contractCallContext, never()).setBalanceCall(true);
    }

    private Opcode executeOperation(final MessageFrame frame) {
        return executeOperation(frame, null);
    }

    private Opcode executeOperation(final MessageFrame frame, final ExceptionalHaltReason haltReason) {
        if (frame.getState() == NOT_STARTED) {
            tracer.traceContextEnter(frame);
        } else {
            tracer.traceContextReEnter(frame);
        }

        final OperationResult operationResult;
        if (haltReason != null) {
            frame.setState(EXCEPTIONAL_HALT);
            frame.setRevertReason(Bytes.of(haltReason.getDescription().getBytes()));
            operationResult = new OperationResult(GAS_COST, haltReason);
        } else {
            operationResult = OPERATION.execute(frame, null);
        }

        tracer.tracePostExecution(frame, operationResult);
        if (frame.getState() == COMPLETED_SUCCESS || frame.getState() == COMPLETED_FAILED) {
            tracer.traceContextExit(frame);
        }

        EXECUTED_FRAMES.set(EXECUTED_FRAMES.get() + 1);
        Opcode expectedOpcode = contractCallContext.getOpcodes().get(EXECUTED_FRAMES.get() - 1);

        verify(contractCallContext, times(1)).addOpcodes(expectedOpcode);
        assertThat(contractCallContext.getOpcodes()).hasSize(1);
        assertThat(contractCallContext.getContractActions()).isNotNull();
        return expectedOpcode;
    }

    private Opcode executePrecompileOperation(final MessageFrame frame, final long gasRequirement, final Bytes output) {
        if (frame.getState() == NOT_STARTED) {
            tracer.traceContextEnter(frame);
        } else {
            tracer.traceContextReEnter(frame);
        }
        tracer.tracePrecompileCall(frame, gasRequirement, output);
        if (frame.getState() == COMPLETED_SUCCESS || frame.getState() == COMPLETED_FAILED) {
            tracer.traceContextExit(frame);
        }

        EXECUTED_FRAMES.set(EXECUTED_FRAMES.get() + 1);
        Opcode expectedOpcode = contractCallContext.getOpcodes().get(EXECUTED_FRAMES.get() - 1);

        verify(contractCallContext, times(1)).addOpcodes(expectedOpcode);
        assertThat(contractCallContext.getOpcodes()).hasSize(EXECUTED_FRAMES.get());
        assertThat(contractCallContext.getContractActions()).isNotNull();
        return expectedOpcode;
    }

    private MessageFrame setupInitialFrame(final OpcodeTracerOptions options) {
        return setupInitialFrame(options, CONTRACT_ADDRESS, MESSAGE_CALL);
    }

    private MessageFrame setupInitialFrame(
            final OpcodeTracerOptions options,
            final Address recipientAddress,
            final MessageFrame.Type type,
            final ContractAction... contractActions) {
        contractCallContext.setOpcodeTracerOptions(options);
        contractCallContext.setContractActions(Lists.newArrayList(contractActions));
        EXECUTED_FRAMES.set(0);

        final MessageFrame messageFrame = buildMessageFrame(recipientAddress, type);
        messageFrame.setState(NOT_STARTED);
        return setupFrame(messageFrame);
    }

    private MessageFrame setupFrame(final MessageFrame messageFrame) {
        reset(contractCallContext);
        stackItems = setupStackForCapture(messageFrame);
        wordsInMemory = setupMemoryForCapture(messageFrame);
        updatedStorage = setupStorageForCapture();
        return messageFrame;
    }

    private Map<UInt256, UInt256> setupStorageForCapture() {
        final Map<UInt256, UInt256> storage = ImmutableSortedMap.of(
                UInt256.ZERO, UInt256.ONE,
                UInt256.ONE, UInt256.ONE);
        final var storageAccesses = new ArrayList<StorageAccesses>();
        final var nestedStorageAccesses = new ArrayList<StorageAccess>();

        final var nestedStorageAccess1 = new StorageAccess(UInt256.ZERO, UInt256.valueOf(233), UInt256.ONE);
        final var nestedStorageAccess2 = new StorageAccess(UInt256.ONE, UInt256.valueOf(2424), UInt256.ONE);
        nestedStorageAccesses.add(nestedStorageAccess1);
        nestedStorageAccesses.add(nestedStorageAccess2);
        final var storageAccess = new StorageAccesses(ContractID.DEFAULT, nestedStorageAccesses);
        storageAccesses.add(storageAccess);
        when(worldUpdater.parentUpdater()).thenReturn(Optional.of(rootProxyWorldUpdater));
        txStorageUsage = new TxStorageUsage(
                storageAccesses,
                Set.of(
                        new SlotKey(ContractID.DEFAULT, com.hedera.pbj.runtime.io.buffer.Bytes.EMPTY),
                        new SlotKey(ContractID.DEFAULT, com.hedera.pbj.runtime.io.buffer.Bytes.wrap("1"))));
        return storage;
    }

    private UInt256[] setupStackForCapture(final MessageFrame frame) {
        final UInt256[] stack = new UInt256[] {
            UInt256.fromHexString("0x01"), UInt256.fromHexString("0x02"), UInt256.fromHexString("0x03")
        };

        for (final UInt256 stackItem : stack) {
            frame.pushStackItem(stackItem);
        }

        return stack;
    }

    private Bytes[] setupMemoryForCapture(final MessageFrame frame) {
        final Bytes[] words = new Bytes[] {
            Bytes.fromHexString("0x01", 32), Bytes.fromHexString("0x02", 32), Bytes.fromHexString("0x03", 32)
        };

        for (int i = 0; i < words.length; i++) {
            frame.writeMemory(i * 32, 32, words[i]);
        }

        return words;
    }

    private MessageFrame buildMessageFrameFromAction(ContractAction action) {
        final var recipientAddress = Address.wrap(Bytes.of(action.getRecipientAddress()));
        final var senderAddress = toAddress(action.getCaller());
        final var value = Wei.of(action.getValue());
        final var type = action.getCallType() == ContractActionType.CREATE_VALUE ? CONTRACT_CREATION : MESSAGE_CALL;
        final var messageFrame = messageFrameBuilder(recipientAddress, type)
                .sender(senderAddress)
                .originator(senderAddress)
                .address(recipientAddress)
                .contract(recipientAddress)
                .inputData(Bytes.of(action.getInput()))
                .initialGas(REMAINING_GAS.get())
                .value(value)
                .apparentValue(value)
                .build();
        messageFrame.setState(NOT_STARTED);
        return messageFrame;
    }

    private MessageFrame buildMessageFrame(final Address recipientAddress, final MessageFrame.Type type) {
        REMAINING_GAS.set(REMAINING_GAS.get() - GAS_COST);

        final MessageFrame messageFrame =
                messageFrameBuilder(recipientAddress, type).build();
        messageFrame.setCurrentOperation(OPERATION);
        messageFrame.setPC(0);
        messageFrame.setGasRemaining(REMAINING_GAS.get());
        return messageFrame;
    }

    private MessageFrame.Builder messageFrameBuilder(final Address recipientAddress, final MessageFrame.Type type) {
        return new MessageFrame.Builder()
                .type(type)
                .code(CodeV0.EMPTY_CODE)
                .sender(Address.ZERO)
                .originator(Address.ZERO)
                .completer(ignored -> {})
                .miningBeneficiary(Address.ZERO)
                .address(recipientAddress)
                .contract(recipientAddress)
                .inputData(Bytes.EMPTY)
                .initialGas(INITIAL_GAS)
                .value(Wei.ZERO)
                .apparentValue(Wei.ZERO)
                .worldUpdater(worldUpdater)
                .gasPrice(Wei.of(GAS_PRICE))
                .blockValues(mock(BlockValues.class))
                .blockHashLookup((ignored, gas) -> Hash.wrap(Bytes32.ZERO))
                .contextVariables(Map.of(ContractCallContext.CONTEXT_NAME, contractCallContext));
    }

    private ContractAction getContractActionNoRevert() {
        return contractAction(0, 0, CallOperationType.OP_CREATE, OUTPUT.getNumber(), CONTRACT_ADDRESS);
    }

    private ContractAction getContractActionWithRevert() {
        return contractAction(1, 1, CallOperationType.OP_CALL, REVERT_REASON.getNumber(), HTS_PRECOMPILE_ADDRESS);
    }

    private static ContractAction contractAction(
            final int index,
            final int depth,
            final CallOperationType callOperationType,
            final int resultDataType,
            final Address recipientAddress) {
        return ContractAction.builder()
                .callDepth(depth)
                .caller(EntityId.of("0.0.1"))
                .callerType(EntityType.ACCOUNT)
                .callOperationType(callOperationType.getNumber())
                .callType(ContractActionType.PRECOMPILE.getNumber())
                .consensusTimestamp(new SecureRandom().nextLong())
                .gas(REMAINING_GAS.get())
                .gasUsed(GAS_PRICE)
                .index(index)
                .input(new byte[0])
                .payerAccountId(EntityId.of("0.0.2"))
                .recipientAccount(EntityId.of("0.0.3"))
                .recipientAddress(recipientAddress.toArray())
                .recipientContract(EntityId.of("0.0.4"))
                .resultData(resultDataType == REVERT_REASON.getNumber() ? "revert reason".getBytes() : new byte[0])
                .resultDataType(resultDataType)
                .value(1L)
                .build();
    }
}
