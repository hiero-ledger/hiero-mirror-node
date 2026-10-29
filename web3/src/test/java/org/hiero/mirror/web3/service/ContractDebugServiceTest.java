// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static com.hedera.services.stream.proto.ContractActionType.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_RECORD_FILE_LATEST;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_RECORD_FILE_LATEST;
import static org.hiero.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.stateOverride;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.service.model.TraceBundle;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.utils.HexUtils;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hiero.mirror.web3.web3j.generated.InternalCaller;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ContractDebugServiceTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration INTEGRATION_TIMEOUT = Duration.ofSeconds(10);
    private static final int NUM_DEPTHS = 4;
    private static final int ACTIONS_PER_DEPTH = 2;

    @MockitoBean
    private ContractActionRepository contractActionRepository;

    @MockitoSpyBean
    private ThrottleManager throttleManager;

    @Qualifier(CACHE_MANAGER_RECORD_FILE_LATEST)
    @Resource
    private CacheManager recordFileLatestCacheManager;

    @Resource
    private RecordFileRepository recordFileRepository;

    @Test
    void processOpcodeCallMapsRevertedActionsToCorrectDepths() {
        // Given – one unique revert message per action, 8 total
        setOpcodeEndpoint();
        stubNestedRevertSimulation();
        final var timestamp = domainBuilder.timestamp();
        final long payerAccountId = domainBuilder.id();
        final var revertedActions = buildRevertedActions(timestamp);

        when(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(timestamp, payerAccountId))
                .thenReturn(revertedActions);

        final var opcodeContext = new OpcodeContext(
                new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false),
                0,
                new OpcodesProperties());

        final var params = ContractDebugParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .consensusTimestamp(timestamp)
                .payerAccountId(payerAccountId)
                .gas(TRANSACTION_GAS_LIMIT)
                .receiver(Address.ZERO)
                .sender(Address.ZERO)
                .value(0L)
                .build();

        // When – processOpcodeCall is invoked for real; only TransactionExecutionService is simulated
        final var result =
                ContractCallContext.run(ctx -> contractDebugService.processOpcodeCall(params, opcodeContext));

        // Then
        final var opcodes = result.opcodes();
        assertThat(opcodes).hasSize(NUM_DEPTHS * ACTIONS_PER_DEPTH);

        // Verify that each opcode carries the revert reason from the correct depth and position
        var opcodeIndex = 0;
        for (var depth = 1; depth <= NUM_DEPTHS; depth++) {
            for (var actionIndex = 0; actionIndex < ACTIONS_PER_DEPTH; actionIndex++) {
                final var opcode = opcodes.get(opcodeIndex);
                final String expectedReason = expectedRevertReason(depth, actionIndex);

                assertThat(opcode.getDepth())
                        .as("opcode[%d] should belong to frame depth %d", opcodeIndex, depth)
                        .isEqualTo(depth);
                assertThat(opcode.getReason())
                        .as(
                                "opcode[%d] at depth %d, action %d should carry the expected revert reason",
                                opcodeIndex, depth, actionIndex)
                        .isEqualTo(expectedReason);

                opcodeIndex++;
            }
        }
    }

    @Test
    void processTraceCallReturnsAllActions() {
        // Given
        final var nestedAction = action("0x01", TypeEnum.CALL);
        final var topLevelAction = action("0x02", TypeEnum.CALL).addCallsItem(nestedAction);
        stubActions(topLevelAction);

        // When
        final var params = executionParameters();
        final var result = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT, null));

        // Then
        assertThat(result).containsExactly(topLevelAction);
        assertThat(result.getFirst().getCalls()).containsExactly(nestedAction);
    }

    @Test
    void processTraceCallReturnsOnlyTopCall() {
        // Given
        final var topLevelAction = action("0x02", TypeEnum.STATICCALL);
        stubActions(topLevelAction);

        // When
        final var params = executionParameters();
        final var result = contractDebugService.processTraceCall(traceCall(params, true, DEFAULT_TIMEOUT, null));

        // Then
        assertThat(result).containsExactly(topLevelAction);
        assertThat(result.getFirst().getCalls()).isNullOrEmpty();
    }

    @Test
    void processTraceCallIntegrationReturnsActions() {
        // Given
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var functionCall = contract.call_multiplySimpleNumbers();
        final var params = getContractExecutionParameters(functionCall, contract);
        publishFixtureBlock();

        // When
        final var result = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT, null));

        // Then
        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().getTo()).isEqualToIgnoringCase(contract.getContractAddress());
    }

    @Test
    void processTraceCallIntegrationOnlyTopCall() {
        // Given
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        final var functionCall = contract.call_callNonExisting(contract.getContractAddress());
        final var params = getContractExecutionParameters(functionCall, contract);
        publishFixtureBlock();

        // When
        final var allActions =
                contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT, null));
        final var topCallOnly =
                contractDebugService.processTraceCall(traceCall(params, true, INTEGRATION_TIMEOUT, null));

        // Then
        assertThat(allActions).hasSize(1);
        assertThat(topCallOnly).hasSize(1);
        assertThat(allActions.getFirst().getCalls()).isNotEmpty();
        assertThat(topCallOnly.getFirst().getCalls()).isNullOrEmpty();
        assertThat(topCallOnly.getFirst().getTo())
                .isEqualTo(allActions.getFirst().getTo());
    }

    @Test
    void processTraceCallManyIntegrationOnlyTopCall() {
        final var contract = testWeb3jService.deploy(InternalCaller::deploy);
        final var functionCall = contract.call_callNonExisting(contract.getContractAddress());
        final var params = getContractExecutionParameters(functionCall, contract);
        publishFixtureBlock();

        final var allActions =
                contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT, null));
        final var topCallsOnly = contractDebugService.processTraceCall(TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(params, params))
                                .build(),
                        TraceBundle.builder().transactions(List.of(params)).build()))
                .onlyTopCall(true)
                .timeout(INTEGRATION_TIMEOUT)
                .build());

        assertThat(allActions).hasSize(1);
        assertThat(allActions.getFirst().getCalls()).isNotEmpty();
        assertThat(topCallsOnly).hasSize(3);
        assertThat(topCallsOnly).allSatisfy(action -> {
            assertThat(action.getCalls()).isNullOrEmpty();
            assertThat(action.getTo()).isEqualTo(allActions.getFirst().getTo());
        });
    }

    @Test
    void processTraceCallAppliesMultipleStateOverrides() {
        final var contract = testWeb3jService.deployWithoutPersist(EthCall::deploy);
        final var account = toAddress(9001L);
        final var functionCall = contract.call_getAccountBalance(account.toHexString());
        final var params = getContractExecutionParameters(functionCall, contract);
        publishFixtureBlock();

        final var codeOverride = new StateOverride();
        codeOverride.setAddress(contract.getContractAddress().toLowerCase());
        codeOverride.setCode(BytecodeUtils.extractRuntimeBytecode(EthCall.BINARY));
        final var balanceOverride = balanceOverride(account, "0x64");

        final var result = contractDebugService.processTraceCall(TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder().transactions(List.of(params)).build()))
                .stateOverrides(List.of(codeOverride, balanceOverride))
                .timeout(INTEGRATION_TIMEOUT)
                .build());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getError()).isNull();
        assertThat(result.getFirst().getTo()).isEqualToIgnoringCase(contract.getContractAddress());
        assertThat(decodedUint256(result.getFirst().getOutput())).isEqualTo(BigInteger.valueOf(100L));
    }

    @Test
    void processTraceCallManyAppliesMultipleStateOverrides() {
        final var contract = testWeb3jService.deploy(EthCall::deploy);
        final var firstAccount = toAddress(9001L);
        final var secondAccount = toAddress(9002L);
        final var firstCall =
                getContractExecutionParameters(contract.call_getAccountBalance(firstAccount.toHexString()), contract);
        final var secondCall =
                getContractExecutionParameters(contract.call_getAccountBalance(secondAccount.toHexString()), contract);
        publishFixtureBlock();

        final var result = contractDebugService.processTraceCall(TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder().transactions(List.of(firstCall)).build(),
                        TraceBundle.builder().transactions(List.of(secondCall)).build()))
                .stateOverrides(List.of(balanceOverride(firstAccount, "0x64"), balanceOverride(secondAccount, "0xc8")))
                .timeout(INTEGRATION_TIMEOUT)
                .build());

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(action -> assertThat(action.getError()).isNull());
        assertThat(decodedUint256(result.get(0).getOutput())).isEqualTo(BigInteger.valueOf(100L));
        assertThat(decodedUint256(result.get(1).getOutput())).isEqualTo(BigInteger.valueOf(200L));
    }

    @Test
    void processTraceCallAppliesRequestedTimeout() {
        final var deadlineWindow = new AtomicLong();
        stubActionsAndCaptureDeadline(deadlineWindow, action("0x02", TypeEnum.CALL));

        contractDebugService.processTraceCall(traceCall(executionParameters(), false, Duration.ofSeconds(2), null));

        assertThat(deadlineWindow).hasValue(Duration.ofSeconds(2).toMillis());
    }

    @Test
    void processTraceCallIncludesSha256Precompile() {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.call_calculateSHA256();
        final var params = getContractExecutionParameters(functionCall, contract);
        publishFixtureBlock();

        final var result = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT, null));

        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().getCalls())
                .extracting(ActionResponse::getTo)
                .anyMatch(to -> Address.SHA256.toHexString().equalsIgnoreCase(to));
    }

    @Test
    void processTraceCallAppliesBlockOverrideNumber() {
        final var captured = new AtomicReference<Long>();
        stubActionsAndCaptureContext(ctx -> captured.set(ctx.getBlockOverrideNumber()), action("0x02", TypeEnum.CALL));
        final var override = new BlockOverride();
        override.setNumber("0x100");

        contractDebugService.processTraceCall(traceCall(executionParameters(), false, DEFAULT_TIMEOUT, override));

        assertThat(captured).hasValue(256L);
    }

    @Test
    void processTraceCallAppliesBlockOverrideTime() {
        final var captured = new AtomicReference<Long>();
        stubActionsAndCaptureContext(
                ctx -> captured.set(ctx.getBlockOverrideTimeNanos()), action("0x02", TypeEnum.CALL));
        final var override = new BlockOverride();
        override.setTime("0x65f9e0c0");

        contractDebugService.processTraceCall(traceCall(executionParameters(), false, DEFAULT_TIMEOUT, override));

        assertThat(captured).hasValue(DomainUtils.convertToNanosMax(HexUtils.parseValue("0x65f9e0c0"), 0));
    }

    @Test
    void processTraceCallManyReturnsOneActionPerRequest() {
        final var first = action("0x01", TypeEnum.CALL);
        final var second = action("0x02", TypeEnum.STATICCALL);
        final var remaining = new ArrayList<>(List.of(first, second));
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    assertThat(ctx.getActionContext()).isNotNull();
                    ctx.getActionContext().addAction(remaining.removeFirst(), 0);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var result = contractDebugService.processTraceCall(TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(result).containsExactly(first, second);
        assertThat(remaining).isEmpty();
    }

    @Test
    void processTraceCallUsesNthTransactionInConsensusOrder() {
        final long first = 1_000L;
        final long second = 2_000L;
        final long third = 3_000L;
        final long fourth = 4_000L;
        final long fifth = 5_000L;
        domainBuilder
                .recordFile()
                .customize(recordFile -> recordFile
                        .index(15L)
                        .consensusStart(first)
                        .consensusEnd(fifth)
                        .count(5L))
                .persist();
        persistTransaction(third);
        persistTransaction(fifth);
        persistTransaction(first);
        persistTransaction(fourth);
        persistTransaction(second);
        persistTransaction(first - 1);
        persistTransaction(fifth + 1);

        final var block = BlockType.of("15");
        assertThat(executionTimestamp(block, 1)).contains(first);
        assertThat(executionTimestamp(block, 2)).contains(second);
        assertThat(executionTimestamp(block, 5)).contains(fifth);
        assertThat(executionTimestamp(block, -1)).contains(fifth);
        assertThat(executionTimestamp(block, 0)).contains(first - 1);

        assertThatThrownBy(() -> contractDebugService.processTraceCall(traceCallAtIndex(block, 6)))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("transaction_index exceeds the number of transactions in the block");
    }

    @Test
    void processTraceCallStartsAtBlockBoundaries() {
        final long consensusStart = 1_000L;
        final long firstTransaction = 2_500L;
        final long lastTransaction = 4_000L;
        final long consensusEnd = 9_000L;
        domainBuilder
                .recordFile()
                .customize(recordFile -> recordFile
                        .index(22L)
                        .consensusStart(consensusStart)
                        .consensusEnd(consensusEnd)
                        .count(2L))
                .persist();
        persistTransaction(lastTransaction);
        persistTransaction(firstTransaction);
        persistTransaction(consensusStart - 1);
        persistTransaction(consensusEnd + 1);

        final var block = BlockType.of("22");
        assertThat(executionTimestamp(block, 0)).contains(consensusStart - 1);
        assertThat(executionTimestamp(block, 1)).contains(firstTransaction);
        assertThat(executionTimestamp(block, 2)).contains(lastTransaction);
        assertThat(executionTimestamp(block, -1)).contains(consensusEnd);

        assertThatThrownBy(() -> contractDebugService.processTraceCall(traceCallAtIndex(block, 3)))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("transaction_index exceeds the number of transactions in the block");
    }

    @Test
    void processTraceCallHistoricalBlockWithoutIndexKeepsConsensusEnd() {
        final long consensusEnd = 9_000L;
        domainBuilder
                .recordFile()
                .customize(recordFile -> recordFile
                        .index(21L)
                        .consensusStart(1_000L)
                        .consensusEnd(consensusEnd)
                        .count(1L))
                .persist();
        final var timed = new BlockOverride();
        timed.setTime("0x65f9e0c0");
        final var timestamps = new ArrayList<Optional<Long>>();
        doAnswer(invocation -> {
                    timestamps.add(ContractCallContext.get().getTimestamp());
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        contractDebugService.processTraceCall(TraceCall.builder()
                .block(BlockType.of("21"))
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .blockOverride(timed)
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(timestamps)
                .containsExactly(Optional.of(consensusEnd), Optional.of(consensusEnd), Optional.of(consensusEnd));
    }

    @Test
    void processTraceCallUsesLatestBlockTransactionIndex() {
        final long consensusTimestamp = genesisRecordFile.getConsensusEnd() + 1_000L;
        domainBuilder
                .recordFile()
                .customize(recordFile ->
                        recordFile.consensusStart(consensusTimestamp).consensusEnd(consensusTimestamp))
                .persist();
        persistTransaction(consensusTimestamp);
        recordFileLatestCacheManager.getCache(CACHE_NAME_RECORD_FILE_LATEST).invalidate();

        assertThat(executionTimestamp(BlockType.LATEST, 1)).contains(consensusTimestamp);
    }

    @Test
    void processTraceCallRejectsTransactionIndexForUnknownBlock() {
        assertThatThrownBy(() -> contractDebugService.processTraceCall(traceCallAtIndex(BlockType.of("999999"), 1)))
                .isInstanceOf(BlockNumberNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-2})
    void processTraceCallRejectsInvalidTransactionIndex(final int transactionIndex) {
        clearInvocations(throttleManager);
        assertThatThrownBy(() ->
                        contractDebugService.processTraceCall(traceCallAtIndex(BlockType.LATEST, transactionIndex)))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("Invalid transaction_index");
        verify(throttleManager).restore(TRANSACTION_GAS_LIMIT);
    }

    @Test
    void processTraceCallAcceptsTransactionIndexZero() {
        final var recordFile =
                domainBuilder.recordFile().customize(file -> file.count(0L)).persist();
        final long consensusStart = recordFile.getConsensusStart();
        final var timestamps = new ArrayList<Optional<Long>>();
        doAnswer(invocation -> {
                    timestamps.add(ContractCallContext.get().getTimestamp());
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        contractDebugService.processTraceCall(TraceCall.builder()
                .block(BlockType.of(Long.toString(recordFile.getIndex())))
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .transactionIndex(0)
                .build());

        final long base = consensusStart - 1;
        assertThat(timestamps).containsExactly(Optional.of(base), Optional.of(base), Optional.of(base));
    }

    @Test
    void processTraceCallKeepsTransactionTimestampTheSame() {
        final long stateTimestamp = 1_700_000_000_000_000_000L;
        final var blockOverride = new BlockOverride();
        blockOverride.setNumber("0x100");
        blockOverride.setTime("0x65f9e0c0");
        final var numberOnly = new BlockOverride();
        numberOnly.setNumber("0x200");
        domainBuilder
                .recordFile()
                .customize(recordFile -> recordFile
                        .index(16L)
                        .consensusStart(stateTimestamp)
                        .consensusEnd(stateTimestamp)
                        .count(1L))
                .persist();
        persistTransaction(stateTimestamp);
        final var timestamps = new ArrayList<Optional<Long>>();
        final var headerTimes = new ArrayList<Long>();
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    final var timestamp = ctx.getTimestamp();
                    timestamps.add(timestamp);
                    headerTimes.add(ctx.getBlockOverrideTimeNanos());
                    assertThat(ctx.getTimestampForSystemFiles()).isEqualTo(timestamp.map(time -> time + 1));
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        contractDebugService.processTraceCall(TraceCall.builder()
                .block(BlockType.of("16"))
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .blockOverride(blockOverride)
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .blockOverride(numberOnly)
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .transactionIndex(1)
                .build());

        final long step = DomainUtils.convertToNanosMax(2L, 0L);
        final long overrideNanos = DomainUtils.convertToNanosMax(HexUtils.parseValue("0x65f9e0c0"), 0L);
        assertThat(timestamps)
                .containsExactly(
                        Optional.of(stateTimestamp),
                        Optional.of(stateTimestamp),
                        Optional.of(stateTimestamp),
                        Optional.of(stateTimestamp),
                        Optional.of(stateTimestamp),
                        Optional.of(stateTimestamp),
                        Optional.of(stateTimestamp));
        assertThat(headerTimes)
                .containsExactly(
                        stateTimestamp,
                        stateTimestamp,
                        stateTimestamp + step,
                        overrideNanos,
                        overrideNanos,
                        overrideNanos + step,
                        overrideNanos + step * 2);
    }

    @Test
    void processTraceCallWithoutTransactionIndexKeepsBlockState() {
        recordFileLatestCacheManager.getCache(CACHE_NAME_RECORD_FILE_LATEST).invalidate();
        final var pinnedEnd = recordFileRepository.findLatest().orElseThrow().getConsensusEnd();
        final var timestamps = new ArrayList<Optional<Long>>();
        doAnswer(invocation -> {
                    timestamps.add(ContractCallContext.get().getTimestamp());
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var timed = new BlockOverride();
        timed.setTime(latestHeaderTimeHex());
        contractDebugService.processTraceCall(TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .blockOverride(timed)
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(timestamps).containsExactly(Optional.of(pinnedEnd), Optional.of(pinnedEnd), Optional.of(pinnedEnd));
    }

    @Test
    void processTraceCallKeepsStateAcrossBundlesAndAppliesBlockOverrides() {
        final var stateOverride = stateOverride("00000000000000000000000000000000000004e4");
        final var blockNumber = new BlockOverride();
        blockNumber.setNumber("0x100");
        final var blockTime = new BlockOverride();
        blockTime.setTime("0x65f9e0c0");
        final var expectedTimeNanos = DomainUtils.convertToNanosMax(HexUtils.parseValue("0x65f9e0c0"), 0);
        final var recordFile = persistRecordFile(40L);
        final var snapshots = new ArrayList<OverrideSnapshot>();

        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    snapshots.add(OverrideSnapshot.capture(ctx));
                    if (snapshots.size() == 1) {
                        ctx.getReadCacheState(1).put("previous-trace", "read");
                        ctx.getWriteCacheState(1).put("previous-trace", "write");
                        ctx.getReadCacheState(BLOCKS_STATE_ID).put("block-header", "cached");
                    }
                    ctx.getActionContext().addAction(action("0x0" + snapshots.size(), TypeEnum.CALL), 0);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var result = contractDebugService.processTraceCall(TraceCall.builder()
                .block(BlockType.of(Long.toString(recordFile.getIndex())))
                .bundles(List.of(
                        TraceBundle.builder()
                                .blockOverride(blockNumber)
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .blockOverride(blockTime)
                                .transactions(List.of(executionParameters()))
                                .build()))
                .stateOverrides(List.of(stateOverride))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(result).hasSize(4);
        assertThat(snapshots).hasSize(4);

        final var firstInBundle = snapshots.get(0);
        assertThat(firstInBundle.stateOverrides()).containsExactly(stateOverride);
        assertThat(firstInBundle.blockNumber()).isEqualTo(256L);
        assertThat(firstInBundle.blockTimeNanos()).isZero();
        assertThat(firstInBundle.readLeaked()).isFalse();
        assertThat(firstInBundle.blockHeaderCached()).isFalse();

        final var secondInBundle = snapshots.get(1);
        assertThat(secondInBundle.stateOverrides()).containsExactly(stateOverride);
        assertThat(secondInBundle.blockNumber()).isEqualTo(256L);
        assertThat(secondInBundle.blockTimeNanos()).isZero();
        assertThat(secondInBundle.readLeaked()).isTrue();
        assertThat(secondInBundle.writeLeaked()).isTrue();
        assertThat(secondInBundle.blockHeaderCached()).isTrue();

        final var nextBundle = snapshots.get(2);
        assertThat(nextBundle.stateOverrides()).containsExactly(stateOverride);
        assertThat(nextBundle.blockNumber()).isEqualTo(257L);
        assertThat(nextBundle.blockTimeNanos()).isEqualTo(DomainUtils.convertToNanosMax(2L, 0L));
        assertThat(nextBundle.readLeaked()).isTrue();
        assertThat(nextBundle.writeLeaked()).isTrue();
        assertThat(nextBundle.blockHeaderCached()).isFalse();

        final var timedBundle = snapshots.get(3);
        assertThat(timedBundle.stateOverrides()).containsExactly(stateOverride);
        assertThat(timedBundle.blockNumber()).isEqualTo(258L);
        assertThat(timedBundle.blockTimeNanos()).isEqualTo(expectedTimeNanos);
        assertThat(timedBundle.readLeaked()).isTrue();
        assertThat(timedBundle.writeLeaked()).isTrue();
        assertThat(timedBundle.blockHeaderCached()).isFalse();
    }

    @Test
    void processTraceCallAdvancesBlockOverrideWhenLaterBundleOmitsIt() {
        final var blockNumber = new BlockOverride();
        blockNumber.setNumber("0x100");
        final var blockTime = new BlockOverride();
        blockTime.setTime("0x65f9e0c0");
        final var expectedTimeNanos = DomainUtils.convertToNanosMax(HexUtils.parseValue("0x65f9e0c0"), 0);
        final var recordFile = persistRecordFile(41L);
        final var twoSecondsNanos = DomainUtils.convertToNanosMax(2L, 0L);
        final var laterNumber = new BlockOverride();
        laterNumber.setNumber("0x200");
        final var snapshots = new ArrayList<OverrideSnapshot>();

        doAnswer(invocation -> {
                    snapshots.add(OverrideSnapshot.capture(ContractCallContext.get()));
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        contractDebugService.processTraceCall(TraceCall.builder()
                .block(BlockType.of(Long.toString(recordFile.getIndex())))
                .bundles(List.of(
                        TraceBundle.builder()
                                .blockOverride(blockNumber)
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .blockOverride(blockTime)
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .blockOverride(laterNumber)
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(snapshots)
                .extracting(OverrideSnapshot::blockNumber)
                .containsExactly(256L, 257L, 258L, 259L, 260L, 512L);
        assertThat(snapshots)
                .extracting(OverrideSnapshot::blockTimeNanos)
                .containsExactly(
                        0L,
                        twoSecondsNanos,
                        twoSecondsNanos * 2,
                        expectedTimeNanos,
                        expectedTimeNanos + twoSecondsNanos,
                        expectedTimeNanos + twoSecondsNanos * 2);
    }

    @Test
    void processTraceCallPropagatesLaterTransactionFailure() {
        final var first = action("0x01", TypeEnum.CALL);
        final var calls = new AtomicInteger();
        doAnswer(invocation -> {
                    if (calls.incrementAndGet() > 1) {
                        throw new MirrorEvmTransactionException(PAYER_ACCOUNT_NOT_FOUND, "sender not found", "0x");
                    }
                    ContractCallContext.get().getActionContext().addAction(first, 0);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var thrown = catchThrowableOfType(
                MirrorEvmTransactionException.class,
                () -> contractDebugService.processTraceCall(TraceCall.builder()
                        .bundles(List.of(
                                TraceBundle.builder()
                                        .transactions(List.of(executionParameters()))
                                        .build(),
                                TraceBundle.builder()
                                        .transactions(List.of(executionParameters()))
                                        .build()))
                        .timeout(DEFAULT_TIMEOUT)
                        .build()));

        assertThat(thrown.getResponseCode()).isEqualTo(PAYER_ACCOUNT_NOT_FOUND);
        assertThat(thrown.getDetail()).isEqualTo("sender not found");
    }

    @Test
    void processTraceCallRestoresUnusedAndUnstartedGas() {
        clearInvocations(throttleManager);
        final var first = action("0x01", TypeEnum.CALL);
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    ctx.getActionContext().addAction(first, 0);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var invalidOverride = new BlockOverride();
        invalidOverride.setNumber("0xzz");
        final var failedParams = ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .callType(ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT / 2)
                .receiver(Address.ZERO)
                .sender(Address.ZERO)
                .value(0L)
                .build();
        final var skippedParams = ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .callType(ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT / 4)
                .receiver(Address.ZERO)
                .sender(Address.ZERO)
                .value(0L)
                .build();

        assertThatThrownBy(() -> contractDebugService.processTraceCall(TraceCall.builder()
                        .bundles(List.of(
                                TraceBundle.builder()
                                        .transactions(List.of(executionParameters()))
                                        .build(),
                                TraceBundle.builder()
                                        .blockOverride(invalidOverride)
                                        .transactions(List.of(failedParams))
                                        .build(),
                                TraceBundle.builder()
                                        .transactions(List.of(skippedParams))
                                        .build()))
                        .timeout(DEFAULT_TIMEOUT)
                        .build()))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessageContaining("Invalid block_override");
        verify(throttleManager).restore(0L);
        verify(throttleManager).restore(TRANSACTION_GAS_LIMIT / 2);
        verify(throttleManager).restore(TRANSACTION_GAS_LIMIT / 4);
    }

    @Test
    void processCallAppliesBlockOverride() {
        final var capturedNumber = new AtomicReference<Long>();
        final var capturedTime = new AtomicReference<Long>();
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    capturedNumber.set(ctx.getBlockOverrideNumber());
                    capturedTime.set(ctx.getBlockOverrideTimeNanos());
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());
        final var override = new BlockOverride();
        override.setNumber("0x100");

        contractExecutionService.processCall(executionParameters(override));

        assertThat(capturedNumber).hasValue(256L);
        assertThat(capturedTime.get()).isNull();
    }

    @Test
    void processCallRejectsInvalidBlockOverride() {
        final var override = new BlockOverride();
        override.setNumber("0xzz");

        assertThatThrownBy(() -> contractExecutionService.processCall(executionParameters(override)))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessageContaining("Invalid block_override");
    }

    /**
     * Overrides the parent spy answer for {@code TransactionExecutionService.execute()} to simulate four nested EVM
     * frames, each containing two reverted system-contract calls.  The doAnswer runs after
     * {@link ContractDebugService#processOpcodeCall} has already called {@code setActions()}, so the
     * {@link OpcodeContext} is fully populated with the mocked actions when we consume them here.
     */
    private void stubNestedRevertSimulation() {
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    final var opcodeContext = ctx.getOpcodeContext();

                    // Simulate 4 nested frames, each with 2 reverted calls to a Hedera system contract.
                    for (var depth = 1; depth <= NUM_DEPTHS; depth++) {
                        for (var i = 0; i < ACTIONS_PER_DEPTH; i++) {
                            final var action = opcodeContext.consumeNextFailedActionAtDepth(depth);
                            final var reason = (action != null && action.hasRevertReason())
                                    ? BytesDecoder.getAbiEncodedRevertReason(
                                            new String(action.getResultData(), StandardCharsets.UTF_8))
                                    : null;
                            opcodeContext.addOpcodes(new Opcode()
                                    .depth(depth)
                                    .reason(reason)
                                    .pc(0)
                                    .op("CALL")
                                    .gas(TRANSACTION_GAS_LIMIT)
                                    .gasCost(0L)
                                    .stack(Collections.emptyList())
                                    .memory(Collections.emptyList())
                                    .storage(Collections.emptyMap()));
                        }
                    }

                    return new EvmTransactionResult(ResponseCodeEnum.SUCCESS, null);
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());
    }

    /**
     * Builds a flat list of reverted {@link ContractAction} records: 2 actions at each of 4 call depths (depths 1–4),
     * ordered so that {@link OpcodeContext#setActions} will sort them correctly within each depth bucket.
     */
    private List<ContractAction> buildRevertedActions(final long consensusTimestamp) {
        final var actions = new ArrayList<ContractAction>(NUM_DEPTHS * ACTIONS_PER_DEPTH);
        for (int depth = 1; depth <= NUM_DEPTHS; depth++) {
            for (int actionIndex = 0; actionIndex < ACTIONS_PER_DEPTH; actionIndex++) {
                actions.add(ContractAction.builder()
                        .callDepth(depth)
                        .index(actionIndex)
                        .callType(SYSTEM.getNumber())
                        .resultDataType(REVERT_REASON.getNumber())
                        .resultData(revertReasonBytes(depth, actionIndex))
                        .consensusTimestamp(consensusTimestamp)
                        .gas(TRANSACTION_GAS_LIMIT)
                        .gasUsed(0L)
                        .value(0L)
                        .build());
            }
        }
        return actions;
    }

    private String latestHeaderTimeHex() {
        recordFileLatestCacheManager.getCache(CACHE_NAME_RECORD_FILE_LATEST).invalidate();
        final var latest = recordFileRepository.findLatest().orElseThrow();
        final var consensusStart = latest.getConsensusStart();
        final long headerSeconds =
                consensusStart == null ? 0L : Math.floorDiv(consensusStart, DomainUtils.NANOS_PER_SECOND);
        return HexUtils.convertLongToHexString(headerSeconds);
    }

    private RecordFile persistRecordFile(final long index) {
        return domainBuilder
                .recordFile()
                .customize(file -> file.index(index)
                        .consensusStart(1_000L)
                        .consensusEnd(2_000L)
                        .count(0L))
                .persist();
    }

    private void persistTransaction(final long consensusTimestamp) {
        domainBuilder
                .transaction()
                .customize(transaction -> transaction
                        .consensusTimestamp(consensusTimestamp)
                        .nonce(0)
                        .parentConsensusTimestamp(null)
                        .type(CONTRACTCALL.getProtoId()))
                .persist();
    }

    private byte[] revertReasonBytes(final int depth, final int actionIndex) {
        return revertMessage(depth, actionIndex).getBytes(StandardCharsets.UTF_8);
    }

    private String revertMessage(final int depth, final int actionIndex) {
        return "Reverted at depth " + depth + ", action " + actionIndex;
    }

    private String expectedRevertReason(final int depth, final int actionIndex) {
        return BytesDecoder.getAbiEncodedRevertReason(revertMessage(depth, actionIndex));
    }

    private void stubActions(final ActionResponse... actions) {
        stubActionsAndCaptureContext(null, actions);
    }

    private void stubActionsAndCaptureDeadline(final AtomicLong deadlineWindow, final ActionResponse... actions) {
        stubActionsAndCaptureContext(
                ctx -> {
                    final var deadlineMillis = ctx.getDeadlineMillis();
                    deadlineWindow.set(deadlineMillis == 0 ? 0 : deadlineMillis - ctx.getStartTime());
                },
                actions);
    }

    private void stubActionsAndCaptureContext(
            final Consumer<ContractCallContext> onExecute, final ActionResponse... actions) {
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    if (onExecute != null) {
                        onExecute.accept(ctx);
                    }
                    final var actionContext = ctx.getActionContext();
                    assertThat(actionContext).isNotNull();
                    for (final var action : actions) {
                        actionContext.addAction(action, 0);
                    }
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());
    }

    private ContractExecutionParameters executionParameters() {
        return executionParameters(null);
    }

    private ContractExecutionParameters executionParameters(final BlockOverride blockOverride) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .blockOverride(blockOverride)
                .callData(new byte[0])
                .callType(ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .receiver(Address.ZERO)
                .sender(Address.ZERO)
                .value(0L)
                .build();
    }

    private Optional<Long> executionTimestamp(final BlockType block, final int transactionIndex) {
        final var timestamps = new ArrayList<Optional<Long>>();
        doAnswer(invocation -> {
                    timestamps.add(ContractCallContext.get().getTimestamp());
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());
        contractDebugService.processTraceCall(traceCallAtIndex(block, transactionIndex));
        assertThat(timestamps).hasSize(1);
        return timestamps.getFirst();
    }

    private TraceCall traceCallAtIndex(final BlockType block, final int transactionIndex) {
        return TraceCall.builder()
                .block(block)
                .bundles(List.of(TraceBundle.builder()
                        .transactions(List.of(executionParameters()))
                        .build()))
                .timeout(DEFAULT_TIMEOUT)
                .transactionIndex(transactionIndex)
                .build();
    }

    private static StateOverride balanceOverride(final Address address, final String balance) {
        final var override = new StateOverride();
        override.setAddress(address.toHexString());
        override.setBalance(balance);
        return override;
    }

    private void publishFixtureBlock() {
        // Fixture deployments happen after the genesis record file. Publish a block containing that state.
        domainBuilder.recordFile().persist();
        recordFileLatestCacheManager.getCache(CACHE_NAME_RECORD_FILE_LATEST).invalidate();
    }

    private static BigInteger decodedUint256(final String output) {
        return Bytes.fromHexString(output).toBigInteger();
    }

    private static TraceCall traceCall(
            final ContractExecutionParameters params,
            final boolean onlyTopCall,
            final Duration timeout,
            final BlockOverride blockOverride) {
        return TraceCall.builder()
                .bundles(List.of(TraceBundle.builder()
                        .blockOverride(blockOverride)
                        .transactions(List.of(params))
                        .build()))
                .onlyTopCall(onlyTopCall)
                .timeout(timeout)
                .build();
    }

    private record OverrideSnapshot(
            List<StateOverride> stateOverrides,
            Long blockNumber,
            Long blockTimeNanos,
            boolean readLeaked,
            boolean writeLeaked,
            boolean blockHeaderCached) {

        private static final String LEAKED_KEY = "previous-trace";
        private static final String BLOCK_HEADER_KEY = "block-header";

        private static OverrideSnapshot capture(final ContractCallContext ctx) {
            final var overrides = ctx.getStateOverrides();
            return new OverrideSnapshot(
                    overrides == null ? List.of() : List.copyOf(overrides.values()),
                    ctx.getBlockOverrideNumber(),
                    ctx.getBlockOverrideTimeNanos(),
                    ctx.getReadCacheState(1).containsKey(LEAKED_KEY),
                    ctx.getWriteCacheState(1).containsKey(LEAKED_KEY),
                    ctx.getReadCacheState(BLOCKS_STATE_ID).containsKey(BLOCK_HEADER_KEY));
        }
    }

    private ActionResponse action(final String from, final TypeEnum type) {
        return new ActionResponse().from(from).to("0x03").type(type).gas("0x0").gasUsed("0x0");
    }
}
