// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.contracts.execution.traceability;

import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.HTS_PRECOMPILED_CONTRACT_ADDRESS;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.OUTPUT;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.convert.BytesDecoder.getAbiEncodedRevertReason;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.utils.Constants.BALANCE_OPERATION_NAME;
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
import static org.mockito.Mockito.lenient;
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
import java.time.Instant;
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
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
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

    // Transient test data
    private OpcodeActionTracer tracer;
    private OpcodeContext opcodeContext;
    private MessageFrame frame;

    // EVM data for capture
    private String[] stackItems;
    private String[] wordsInMemory;
    private Map<String, String> updatedStorage;
    private TxStorageUsage txStorageUsage;

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
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

    @BeforeEach
    void setUp() {
        REMAINING_GAS.set(INITIAL_GAS);
        tracer = new OpcodeActionTracer();
        tracer.setSystemContracts(Map.of(HTS_PRECOMPILE_ADDRESS, mock(HederaSystemContract.class)));
        opcodeContext = new OpcodeContext(
                new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false), 0);
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
        lenient().when(contractCallContext.getOpcodeContext()).thenReturn(opcodeContext);
    }

    @AfterEach
    void tearDown() {
        verifyMocks();
        reset(contractCallContext);
        reset(worldUpdater);
        reset(recipientAccount);
    }

    private void verifyMocks() {
        if (opcodeContext.isStorage()) {
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
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getPc()).isEqualTo(frame.getPC());
    }

    @Test
    @DisplayName("should record opcode")
    void shouldRecordOpcode() {
        // Given
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getOp()).isNotEmpty();
        assertThat(opcode.getOp()).contains(OPERATION.getName());
    }

    @Test
    @DisplayName("should record depth")
    void shouldRecordDepth() {
        // Given
        frame = setupInitialFrame(opcodeContext);

        // simulate 4 calls
        final int expectedDepth = 4;
        for (int i = 0; i < expectedDepth; i++) {
            frame.getMessageFrameStack()
                    .add(buildMessageFrame(Address.fromHexString("0x10%d".formatted(i)), MESSAGE_CALL));
        }

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getDepth()).isEqualTo(expectedDepth);
    }

    @Test
    @DisplayName("should record remaining gas")
    void shouldRecordRemainingGas() {
        // Given
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getGas()).isEqualTo(REMAINING_GAS.get());
    }

    @Test
    @DisplayName("should record gas cost")
    void shouldRecordGasCost() {
        // Given
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getGasCost()).isEqualTo(GAS_COST);
    }

    @Test
    @DisplayName("given stack is enabled in tracer options, should record stack")
    void shouldRecordStackWhenEnabled() {
        // Given
        opcodeContext = opcodeContext.toBuilder().stack(true).build();
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getStack()).isNotEmpty();
        assertThat(opcode.getStack()).containsExactly(stackItems);
    }

    @Test
    @DisplayName("given stack is disabled in tracer options, should not record stack")
    void shouldNotRecordStackWhenDisabled() {
        // Given
        opcodeContext = opcodeContext.toBuilder().stack(false).build();
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getStack()).isEmpty();
    }

    @Test
    @DisplayName("given memory is enabled in tracer options, should record memory")
    void shouldRecordMemoryWhenEnabled() {
        // Given
        opcodeContext = opcodeContext.toBuilder().memory(true).build();
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getMemory()).isNotEmpty();
        assertThat(opcode.getMemory()).containsExactly(wordsInMemory);
    }

    @Test
    @DisplayName("given memory is disabled in tracer options, should not record memory")
    void shouldNotRecordMemoryWhenDisabled() {
        // Given
        opcodeContext = opcodeContext.toBuilder().memory(false).build();
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getMemory()).isEmpty();
    }

    @Test
    @DisplayName("given storage is enabled in tracer options, should record storage")
    void shouldRecordStorage() {
        // Given
        opcodeContext = opcodeContext.toBuilder().storage(true).build();
        frame = setupInitialFrame(opcodeContext);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(txStorageUsage);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getStorage()).isNotEmpty().containsAllEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName(
            "given storage is enabled in tracer options, should return empty storage when there are no updates for modularized services")
    void shouldReturnEmptyStorageWhenThereAreNoUpdates() {
        // Given
        opcodeContext = opcodeContext.toBuilder().storage(true).build();
        frame = setupInitialFrame(opcodeContext);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(new TxStorageUsage(List.of(), Set.of()));

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getStorage()).isEmpty();
    }

    @Test
    @DisplayName("given account is missing in the world updater, should only log a warning and return empty storage")
    void shouldNotThrowExceptionWhenAccountIsMissingInWorldUpdater() {
        // Given
        opcodeContext = opcodeContext.toBuilder().storage(true).build();
        frame = setupInitialFrame(opcodeContext);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(new TxStorageUsage(List.of(), Set.of()));

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getStorage()).containsExactlyEntriesOf(new TreeMap<>());
    }

    @Test
    @DisplayName("should reflect latest slot value when same key is overwritten with unchanged access count")
    void shouldReturnUpdatedSlotValueWhenCountIsUnchangedBetweenOpcodes() {
        // Given
        opcodeContext = opcodeContext.toBuilder().storage(true).build();
        frame = setupInitialFrame(opcodeContext);
        when(contractCallContext.getOpcodeContext()).thenReturn(opcodeContext);

        final var key = UInt256.ONE;
        final var v1 = UInt256.valueOf(100);
        final var v2 = UInt256.valueOf(200);

        // Opcode X: slot key=1, written to v1 (1 access total)
        final var access1 = StorageAccess.newWrite(key, UInt256.ZERO, v1);
        final var tx1 =
                new TxStorageUsage(List.of(new StorageAccesses(ContractID.DEFAULT, List.of(access1))), Set.of());

        // Opcode X+1: same slot key=1 overwritten to v2 — StorageAccessTracker replaces in-place,
        // so total access count is still 1; only the writtenValue changes.
        final var access2 = StorageAccess.newWrite(key, v1, v2);
        final var tx2 =
                new TxStorageUsage(List.of(new StorageAccesses(ContractID.DEFAULT, List.of(access2))), Set.of());

        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(tx1, tx2);

        // When - first opcode sees v1
        final Opcode opcode1 = executeOperation(frame);
        assertThat(opcode1.getStorage()).containsEntry(key.toHexString(), v1.toHexString());

        // When - second opcode: same access count (1) but value changed to v2
        final Opcode opcode2 = executeOperation(frame);

        // Then - must report v2, not the stale cached v1
        assertThat(opcode2.getStorage()).containsEntry(key.toHexString(), v2.toHexString());
    }

    @Test
    @DisplayName("should correctly track storage through multiple sequential overwrites of the same slot")
    void shouldTrackStorageThroughMultipleSequentialOverwritesOfSameSlot() {
        // Given
        opcodeContext = opcodeContext.toBuilder().storage(true).build();
        frame = setupInitialFrame(opcodeContext);
        when(contractCallContext.getOpcodeContext()).thenReturn(opcodeContext);

        final var key = UInt256.ONE;
        final var v1 = UInt256.valueOf(10);
        final var v2 = UInt256.valueOf(20);
        final var v3 = UInt256.valueOf(30);

        // Three successive writes to the same key: access count remains 1 throughout.
        final var tx1 = new TxStorageUsage(
                List.of(new StorageAccesses(
                        ContractID.DEFAULT, List.of(StorageAccess.newWrite(key, UInt256.ZERO, v1)))),
                Set.of());
        final var tx2 = new TxStorageUsage(
                List.of(new StorageAccesses(ContractID.DEFAULT, List.of(StorageAccess.newWrite(key, v1, v2)))),
                Set.of());
        final var tx3 = new TxStorageUsage(
                List.of(new StorageAccesses(ContractID.DEFAULT, List.of(StorageAccess.newWrite(key, v2, v3)))),
                Set.of());

        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(tx1, tx2, tx3);

        // When / Then
        assertThat(executeOperation(frame).getStorage()).containsEntry(key.toHexString(), v1.toHexString());
        assertThat(executeOperation(frame).getStorage()).containsEntry(key.toHexString(), v2.toHexString());
        assertThat(executeOperation(frame).getStorage()).containsEntry(key.toHexString(), v3.toHexString());
    }

    @Test
    @DisplayName("given storage is disabled in tracer options, should not record storage")
    void shouldNotRecordStorageWhenDisabled() {
        // Given
        opcodeContext = opcodeContext.toBuilder().storage(false).build();
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executeOperation(frame);

        // Then
        assertThat(opcode.getStorage()).isEmpty();
    }

    @Test
    @DisplayName("given exceptional halt occurs, should capture frame data and halt reason")
    void shouldCaptureFrameWhenExceptionalHaltOccurs() {
        // Given
        opcodeContext =
                opcodeContext.toBuilder().stack(true).memory(true).storage(true).build();
        frame = setupInitialFrame(opcodeContext);
        when(rootProxyWorldUpdater.getEvmFrameState()).thenReturn(evmFrameState);
        when(evmFrameState.getTxStorageUsage(anyBoolean())).thenReturn(txStorageUsage);

        // When
        final Opcode opcode = executeOperation(frame, INSUFFICIENT_GAS);

        // Then
        assertThat(opcode.getReason())
                .contains(Hex.encodeHexString(INSUFFICIENT_GAS.getDescription().getBytes()));
        assertThat(opcode.getStack()).contains(stackItems);
        assertThat(opcode.getMemory()).contains(wordsInMemory);
        assertThat(opcode.getStorage()).containsExactlyEntriesOf(updatedStorage);
    }

    @Test
    @DisplayName("should capture a precompile call")
    void shouldCaptureFrameWhenSuccessfulPrecompileCallOccurs() {
        // Given
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcode.getPc()).isEqualTo(frame.getPC());
        assertThat(opcode.getOp()).isNotEmpty().isEqualTo(OPERATION.getName());
        assertThat(opcode.getGas()).isEqualTo(REMAINING_GAS.get());
        assertThat(opcode.getGasCost()).isEqualTo(GAS_REQUIREMENT);
        assertThat(opcode.getDepth()).isEqualTo(frame.getDepth());
        assertThat(opcode.getStack()).isEmpty();
        assertThat(opcode.getMemory()).isEmpty();
        assertThat(opcode.getStorage()).isEmpty();
        assertThat(opcode.getReason())
                .isEqualTo(frame.getRevertReason().map(Bytes::toHexString).orElse(null));
    }

    @Test
    @DisplayName("should not record revert reason of precompile call with no revert reason")
    void shouldNotRecordRevertReasonWhenPrecompileCallHasNoRevertReason() {
        // Given
        frame = setupInitialFrame(opcodeContext);

        // When
        final Opcode opcode = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcode.getReason()).isNull();
    }

    @Test
    @DisplayName("should record revert reason of precompile call when frame has revert reason")
    void shouldRecordRevertReasonWhenEthPrecompileCallHasRevertReason() {
        // Given
        frame = setupInitialFrame(opcodeContext, ETH_PRECOMPILE_ADDRESS, MESSAGE_CALL);
        frame.setRevertReason(Bytes.of("revert reason".getBytes()));

        // When
        final Opcode opcode = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcode.getReason())
                .isNotEmpty()
                .isEqualTo(frame.getRevertReason().map(Bytes::toHexString).orElseThrow());
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with plaintext revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithPlaintextRevertReason() {
        // Given
        final var contractActionNoRevert = getContractActionNoRevert();
        final var contractActionWithRevert = getContractActionWithRevert();
        contractActionWithRevert.setResultData("revert reason".getBytes());

        frame = setupInitialFrame(
                opcodeContext, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.getReason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.getReason())
                .isNotEmpty()
                .isEqualTo(getAbiEncodedRevertReason(new String(contractActionWithRevert.getResultData())));
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
                opcodeContext, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.getReason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.getReason())
                .isNotEmpty()
                .isEqualTo(getAbiEncodedRevertReason(
                        new String(ResponseCodeEnum.INVALID_ACCOUNT_ID.name().getBytes())));
    }

    @Test
    @DisplayName("should return ABI-encoded revert reason for precompile call with ABI-encoded revert reason")
    void shouldReturnAbiEncodedRevertReasonWhenPrecompileCallHasContractActionWithAbiEncodedRevertReason() {
        // Given
        final var contractActionNoRevert = getContractActionNoRevert();
        final var contractActionWithRevert =
                contractAction(1, 1, CallOperationType.OP_CALL, REVERT_REASON.getNumber(), HTS_PRECOMPILE_ADDRESS);
        contractActionWithRevert.setResultData(Bytes.fromHexString(getAbiEncodedRevertReason(
                        new String(INVALID_OPERATION.name().getBytes())))
                .toArray());

        frame = setupInitialFrame(
                opcodeContext, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.getReason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.getReason())
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
                opcodeContext, CONTRACT_ADDRESS, MESSAGE_CALL, contractActionNoRevert, contractActionWithRevert);

        // When
        final Opcode opcode = executeOperation(frame);
        assertThat(opcode.getReason()).isNull();

        final var frameOfPrecompileCall = buildMessageFrameFromAction(contractActionWithRevert);
        frame = setupFrame(frameOfPrecompileCall);

        final Opcode opcodeForPrecompileCall = executePrecompileOperation(frame, GAS_REQUIREMENT, DEFAULT_OUTPUT);

        // Then
        assertThat(opcodeForPrecompileCall.getReason()).isNotNull().isEqualTo(Bytes.EMPTY.toHexString());
    }

    @Test
    @DisplayName("should set balance call flag when BALANCE opcode is executed")
    void shouldSetBalanceCallFlagForBalanceOperation() {
        // Given
        final var balanceOperation = new AbstractOperation(0x31, BALANCE_OPERATION_NAME, 1, 1, null) {
            @Override
            public OperationResult execute(final MessageFrame frame, final EVM evm) {
                return new OperationResult(GAS_COST, null);
            }
        };
        frame = setupInitialFrame(opcodeContext);
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
        frame = setupInitialFrame(opcodeContext);
        frame.setCurrentOperation(OPERATION);

        // When
        tracer.tracePreExecution(frame);

        // Then
        verify(contractCallContext, never()).setBalanceCall(true);
    }

    @Test
    @DisplayName("should not throw exception when current operation is null")
    void shouldHandleNullCurrentOperation() {
        // Given
        frame = setupInitialFrame(opcodeContext);
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
        Opcode expectedOpcode =
                contractCallContext.getOpcodeContext().getOpcodes().get(EXECUTED_FRAMES.get() - 1);

        assertThat(contractCallContext.getOpcodeContext().getOpcodes()).hasSize(EXECUTED_FRAMES.get());
        assertThat(contractCallContext.getOpcodeContext().getActions()).isNotNull();
        return expectedOpcode;
    }

    private Opcode executePrecompileOperation(final MessageFrame frame, final long gasRequirement, final Bytes output) {
        if (frame.getState() == NOT_STARTED) {
            tracer.traceContextEnter(frame);
        } else {
            tracer.traceContextReEnter(frame);
        }

        // Simulate gas consumption by the precompile before tracing the result.
        // In a real EVM execution, the precompile consumes gas before tracePrecompileResult
        REMAINING_GAS.set(REMAINING_GAS.get() - gasRequirement);
        frame.setGasRemaining(REMAINING_GAS.get());

        tracer.tracePrecompileResult(frame, com.hedera.hapi.streams.ContractActionType.CALL);
        if (frame.getState() == COMPLETED_SUCCESS || frame.getState() == COMPLETED_FAILED) {
            tracer.traceContextExit(frame);
        }

        EXECUTED_FRAMES.set(EXECUTED_FRAMES.get() + 1);
        Opcode expectedOpcode =
                contractCallContext.getOpcodeContext().getOpcodes().get(EXECUTED_FRAMES.get() - 1);

        assertThat(contractCallContext.getOpcodeContext().getOpcodes()).hasSize(EXECUTED_FRAMES.get());
        assertThat(contractCallContext.getOpcodeContext().getActions()).isNotNull();
        return expectedOpcode;
    }

    private MessageFrame setupInitialFrame(final OpcodeContext options) {
        return setupInitialFrame(options, CONTRACT_ADDRESS, MESSAGE_CALL);
    }

    private MessageFrame setupInitialFrame(
            final OpcodeContext opcodeContext,
            final Address recipientAddress,
            final MessageFrame.Type type,
            final ContractAction... contractActions) {
        this.opcodeContext = opcodeContext;
        when(contractCallContext.getOpcodeContext()).thenReturn(opcodeContext);
        contractCallContext.setOpcodeContext(opcodeContext);
        contractCallContext.getOpcodeContext().setActions(Lists.newArrayList(contractActions));
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

    private Map<String, String> setupStorageForCapture() {
        final Map<String, String> storage = ImmutableSortedMap.of(
                UInt256.ZERO.toHexString(), UInt256.ONE.toHexString(),
                UInt256.ONE.toHexString(), UInt256.ONE.toHexString());
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

    private String[] setupStackForCapture(final MessageFrame frame) {
        final UInt256[] stack = new UInt256[] {
            UInt256.fromHexString("0x01"), UInt256.fromHexString("0x02"), UInt256.fromHexString("0x03")
        };

        for (final UInt256 stackItem : stack) {
            frame.pushStackItem(stackItem);
        }

        return new String[] {stack[0].toHexString(), stack[1].toHexString(), stack[2].toHexString()};
    }

    private String[] setupMemoryForCapture(final MessageFrame frame) {
        final Bytes[] words = new Bytes[] {
            Bytes.fromHexString("0x01", 32), Bytes.fromHexString("0x02", 32), Bytes.fromHexString("0x03", 32)
        };

        for (int i = 0; i < words.length; i++) {
            frame.writeMemory(i * 32, 32, words[i]);
        }

        return new String[] {words[0].toHexString(), words[1].toHexString(), words[2].toHexString()};
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
}
