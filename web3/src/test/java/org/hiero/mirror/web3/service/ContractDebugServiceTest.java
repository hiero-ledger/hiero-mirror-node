// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
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
import static org.mockito.Mockito.doAnswer;
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
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
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
import org.hiero.mirror.web3.utils.BytecodeUtils;
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

class ContractDebugServiceTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration INTEGRATION_TIMEOUT = Duration.ofSeconds(10);
    private static final int NUM_DEPTHS = 4;
    private static final int ACTIONS_PER_DEPTH = 2;

    @MockitoBean
    private ContractActionRepository contractActionRepository;

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
        final var result = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT));

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
        final var result = contractDebugService.processTraceCall(traceCall(params, true, DEFAULT_TIMEOUT));

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
        final var result = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT));

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
        final var allActions = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT));
        final var topCallOnly = contractDebugService.processTraceCall(traceCall(params, true, INTEGRATION_TIMEOUT));

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

        final var allActions = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT));
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
        final var balanceOverride = balanceOverride(account, 100L);

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
                .stateOverrides(List.of(balanceOverride(firstAccount, 100L), balanceOverride(secondAccount, 200L)))
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

        contractDebugService.processTraceCall(traceCall(executionParameters(), false, Duration.ofSeconds(2)));

        assertThat(deadlineWindow).hasValue(Duration.ofSeconds(2).toMillis());
    }

    @Test
    void processTraceCallIncludesSha256Precompile() {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.call_calculateSHA256();
        final var params = getContractExecutionParameters(functionCall, contract);
        publishFixtureBlock();

        final var result = contractDebugService.processTraceCall(traceCall(params, false, INTEGRATION_TIMEOUT));

        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().getCalls())
                .extracting(ActionResponse::getTo)
                .anyMatch(to -> Address.SHA256.toHexString().equalsIgnoreCase(to));
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
        // A positive index N reads state one nanosecond after the Nth EVM transaction.
        assertThat(executionTimestamp(block, 1)).contains(first + 1);
        assertThat(executionTimestamp(block, 2)).contains(second + 1);
        assertThat(executionTimestamp(block, 4)).contains(fourth + 1);
        assertThat(executionTimestamp(block, 5)).contains(fifth + 1);
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
        assertThat(executionTimestamp(block, 1)).contains(firstTransaction + 1);
        assertThat(executionTimestamp(block, 2)).contains(lastTransaction + 1);
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
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(timestamps)
                .containsExactly(Optional.of(consensusEnd), Optional.of(consensusEnd), Optional.of(consensusEnd));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 100})
    void processTraceCallReadsCurrentStateForSingleTransactionAtLatest(final int transactionIndex) {
        final long consensusStart = genesisRecordFile.getConsensusEnd() + 1_000L;
        final long consensusEnd = consensusStart + 5_000L;
        domainBuilder
                .recordFile()
                .customize(
                        recordFile -> recordFile.consensusStart(consensusStart).consensusEnd(consensusEnd))
                .persist();
        persistTransaction(consensusStart + 1_000L);
        persistTransaction(consensusStart + 2_000L);
        recordFileLatestCacheManager.getCache(CACHE_NAME_RECORD_FILE_LATEST).invalidate();

        // Like eth_call, a single transaction at latest reads current state, so no timestamp is pinned.
        assertThat(executionTimestamp(BlockType.LATEST, transactionIndex)).isEmpty();
    }

    @Test
    void processTraceCallAppliesTransactionIndexToBlockNumberOfLatestRecordFile() {
        final long consensusStart = genesisRecordFile.getConsensusEnd() + 1_000L;
        final long secondTransaction = consensusStart + 2_000L;
        final var recordFile = domainBuilder
                .recordFile()
                .customize(r -> r.consensusStart(consensusStart).consensusEnd(consensusStart + 5_000L))
                .persist();
        persistTransaction(consensusStart + 1_000L);
        persistTransaction(secondTransaction);
        recordFileLatestCacheManager.getCache(CACHE_NAME_RECORD_FILE_LATEST).invalidate();

        assertThat(executionTimestamp(BlockType.of(recordFile.getIndex().toString()), 2))
                .contains(secondTransaction + 1);
    }

    @Test
    void processTraceCallRejectsTransactionIndexForUnknownBlock() {
        assertThatThrownBy(() -> contractDebugService.processTraceCall(traceCallAtIndex(BlockType.of("999999"), 1)))
                .isInstanceOf(BlockNumberNotFoundException.class);
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
        domainBuilder
                .recordFile()
                .customize(recordFile -> recordFile
                        .index(16L)
                        .consensusStart(stateTimestamp)
                        .consensusEnd(stateTimestamp + 1_000L)
                        .count(2L))
                .persist();
        persistTransaction(stateTimestamp);
        // transaction_index 2 selects this second transaction.
        persistTransaction(stateTimestamp + 1_000L);
        final var timestamps = new ArrayList<Optional<Long>>();
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    final var timestamp = ctx.getTimestamp();
                    timestamps.add(timestamp);
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
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .transactionIndex(2)
                .build());

        final var stateRead = Optional.of(stateTimestamp + 1_001L);
        assertThat(timestamps)
                .containsExactly(stateRead, stateRead, stateRead, stateRead, stateRead, stateRead, stateRead);
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

        contractDebugService.processTraceCall(TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(timestamps).containsExactly(Optional.of(pinnedEnd), Optional.of(pinnedEnd), Optional.of(pinnedEnd));
    }

    @Test
    void processTraceCallKeepsStateAcrossBundles() {
        final var stateOverride = stateOverride("00000000000000000000000000000000000004e4");
        final var recordFile = persistRecordFile(40L);
        final var snapshots = new ArrayList<OverrideSnapshot>();

        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    snapshots.add(OverrideSnapshot.capture(ctx));
                    if (snapshots.size() == 1) {
                        ctx.getReadCacheState(1).put("previous-trace", "read");
                        ctx.getWriteCacheState(1).put("previous-trace", "write");
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
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build()))
                .stateOverrides(List.of(stateOverride))
                .timeout(DEFAULT_TIMEOUT)
                .build());

        assertThat(result).hasSize(4);
        assertThat(snapshots).hasSize(4);

        final var firstInBundle = snapshots.get(0);
        assertThat(firstInBundle.stateOverrides()).containsExactly(stateOverride);
        assertThat(firstInBundle.readLeaked()).isFalse();

        for (final var later : snapshots.subList(1, snapshots.size())) {
            assertThat(later.stateOverrides()).containsExactly(stateOverride);
            assertThat(later.readLeaked()).isTrue();
            assertThat(later.writeLeaked()).isTrue();
        }
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
    void processTraceCallRefundsUnusedGasAndGasOfTransactionsThatDidNotRun() {
        final var first = action("0x01", TypeEnum.CALL);
        final var calls = new AtomicInteger();
        // Each transaction that runs uses a third of its gas. The second bundle's transaction fails, so the third
        // bundle never starts.
        doAnswer(invocation -> {
                    final long gasLimit = invocation.getArgument(1);
                    final var functionResult = ContractFunctionResult.newBuilder()
                            .gasUsed(gasLimit / 3)
                            .build();
                    if (calls.incrementAndGet() > 1) {
                        throw new MirrorEvmTransactionException(
                                PAYER_ACCOUNT_NOT_FOUND.name(),
                                "sender not found",
                                "0x",
                                new EvmTransactionResult(PAYER_ACCOUNT_NOT_FOUND, functionResult));
                    }
                    ContractCallContext.get().getActionContext().addAction(first, 0);
                    return new EvmTransactionResult(SUCCESS, functionResult);
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

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

        final var traceCall = TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(failedParams))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(skippedParams))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build();

        final long refundableGas = ContractCallContext.run(context -> {
            assertThatThrownBy(() -> contractDebugService.processTraceCall(traceCall, context))
                    .isInstanceOf(MirrorEvmTransactionException.class)
                    .hasMessageContaining(PAYER_ACCOUNT_NOT_FOUND.name());
            return context.getRefundableGas();
        });

        assertThat(calls).hasValue(2);
        // The unused two thirds of each transaction that ran, plus the whole gas limit of the one that did not.
        assertThat(refundableGas)
                .isEqualTo(
                        TRANSACTION_GAS_LIMIT * 2 / 3 + TRANSACTION_GAS_LIMIT / 2 * 2 / 3 + TRANSACTION_GAS_LIMIT / 4);
    }

    @Test
    void processTraceCallRefundsUnusedGasOfEveryTransaction() {
        // Each transaction uses a third of its gas.
        doAnswer(invocation -> {
                    final long gasLimit = invocation.getArgument(1);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(gasLimit / 3)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());
        final var traceCall = TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters(), executionParameters()))
                                .build(),
                        TraceBundle.builder()
                                .transactions(List.of(executionParameters()))
                                .build()))
                .timeout(DEFAULT_TIMEOUT)
                .build();

        final long refundableGas = ContractCallContext.run(context -> {
            contractDebugService.processTraceCall(traceCall, context);
            return context.getRefundableGas();
        });

        assertThat(refundableGas).isEqualTo(3 * TRANSACTION_GAS_LIMIT * 2 / 3);
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
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
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

    private static StateOverride balanceOverride(final Address address, final long balance) {
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
            final ContractExecutionParameters params, final boolean onlyTopCall, final Duration timeout) {
        return TraceCall.builder()
                .bundles(List.of(
                        TraceBundle.builder().transactions(List.of(params)).build()))
                .onlyTopCall(onlyTopCall)
                .timeout(timeout)
                .build();
    }

    private record OverrideSnapshot(List<StateOverride> stateOverrides, boolean readLeaked, boolean writeLeaked) {

        private static final String LEAKED_KEY = "previous-trace";

        private static OverrideSnapshot capture(final ContractCallContext ctx) {
            final var overrides = ctx.getStateOverrides();
            return new OverrideSnapshot(
                    overrides == null ? List.of() : List.copyOf(overrides.values()),
                    ctx.getReadCacheState(1).containsKey(LEAKED_KEY),
                    ctx.getWriteCacheState(1).containsKey(LEAKED_KEY));
        }
    }

    private ActionResponse action(final String from, final TypeEnum type) {
        return new ActionResponse().from(from).to("0x03").type(type).gas("0x0").gasUsed("0x0");
    }
}
