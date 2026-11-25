// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static com.hedera.services.stream.proto.ContractActionType.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.hasSize;
import static org.hiero.mirror.common.domain.transaction.TransactionType.CONTRACTCALL;
import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.node.app.records.BlockRecordService;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.ApiProperties;
import org.hiero.mirror.web3.Web3Properties;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.ActionsProperties;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.TraceTimeoutException;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.RecordFileRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.state.MirrorNodeState;
import org.hiero.mirror.web3.utils.BytecodeUtils;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.viewmodel.StateOverride;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hiero.mirror.web3.web3j.generated.InternalCaller;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class ContractDebugServiceTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final String ACTIONS_URI = "/api/v1/contracts/call/actions";
    private static final int NUM_DEPTHS = 4;
    private static final int ACTIONS_PER_DEPTH = 2;

    @MockitoBean
    private ContractActionRepository contractActionRepository;

    @Qualifier(CACHE_MANAGER_RECORD_FILE_LATEST)
    @Resource
    private CacheManager recordFileLatestCacheManager;

    @Resource
    private RecordFileRepository recordFileRepository;

    @Resource
    private MirrorNodeState mirrorNodeState;

    @Resource
    private TransactionExecutorFactory transactionExecutorFactory;

    @Resource
    private ActionsProperties actionsProperties;

    @Resource
    private MockMvc mockMvc;

    @Resource
    private Web3Properties web3Properties;

    private boolean actionsEnabled;
    private boolean blockOverridesEnabled;
    private boolean traceMultipleTransactions;

    @BeforeEach
    void setUp() {
        // Executors are built asynchronously at startup. Wait for them here so the first trace in a fresh context does
        // not spend its request timeout on that.
        ContractCallContext.run(_ -> transactionExecutorFactory.get());
        actionsEnabled = actionsApi().isEnabled();
        blockOverridesEnabled = web3Properties.isEnableBlockOverrides();
        traceMultipleTransactions = actionsProperties.isTraceMultipleTransactions();
    }

    @AfterEach
    void tearDown() {
        actionsApi().setEnabled(actionsEnabled);
        web3Properties.setEnableBlockOverrides(blockOverridesEnabled);
        actionsProperties.setTraceMultipleTransactions(traceMultipleTransactions);
    }

    @Test
    void actionsEndpointTracesEveryBundle() throws Exception {
        final var evmCodes = testWeb3jService.deploy(EvmCodes::deploy);
        final var ethCall = testWeb3jService.deploy(EthCall::deploy);
        final var recordFile = publishFixtureBlock();
        final var body = """
                {"bundles": [{"block_override": {"block_number": 80},
                              "transactions": [{"data": "%1$s", "to": "%2$s"}]},
                             {"transactions": [{"data": "%1$s", "to": "%2$s"}, {"data": "%3$s", "to": "%4$s"}]}]}
                """.formatted(
                        evmCodes.call_getBlockNumber().encodeFunctionCall(),
                        evmCodes.getContractAddress(),
                        ethCall.send_testRevert().encodeFunctionCall(),
                        ethCall.getContractAddress());

        actionsCall(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].to", equalToIgnoringCase(evmCodes.getContractAddress())))
                .andExpect(jsonPath("$[0].output").value(uint256Hex(80L)))
                .andExpect(jsonPath("$[1].output").value(uint256Hex(recordFile.getIndex())))
                .andExpect(jsonPath("$[2].error").value("execution reverted"))
                .andExpect(jsonPath("$[2].revert_reason").value("Custom revert message"));
    }

    @Test
    void actionsEndpointReturnsTheActionsSoFarWhenTheTraceTimesOut() throws Exception {
        stubQueryTimeoutAfterTheFirstTransaction(action("0x01", TypeEnum.CALL));
        final var body = """
                {"bundles": [{"transactions": [{"to": "%1$s"}]}, {"transactions": [{"to": "%1$s"}]}]}
                """.formatted(Address.ZERO.toHexString());

        actionsCall(body)
                .andExpect(status().isRequestTimeout())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].from").value("0x01"));
    }

    @Test
    void actionsEndpointRejectsASenderThatDoesNotExist() throws Exception {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        publishFixtureBlock();
        final var body = """
                {"bundles": [{"transactions": [{"data": "%s", "from": "%s", "to": "%s"}]}]}
                """.formatted(
                        contract.call_getBlockNumber().encodeFunctionCall(),
                        toAddress(9_999_999L).toHexString(),
                        contract.getContractAddress());

        actionsCall(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString(PAYER_ACCOUNT_NOT_FOUND.name())))
                .andExpect(content().string(containsString("Sender account not found.")));
    }

    @Test
    void processTraceCallAppliesTheBlockOverrideOfEachTransaction() {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var recordFile = publishFixtureBlock();
        final long firstTime = 1_793_619_195L;
        final var first =
                BlockOverride.builder().blockNumber(80L).time(firstTime).build();
        final var second = BlockOverride.builder().blockNumber(90L).build();
        final var number = getContractExecutionParameters(contract.call_getBlockNumber(), contract);
        final var time = getContractExecutionParameters(contract.call_getBlockTimestamp(), contract);

        final var result = trace(TraceCall.builder()
                .transactions(List.of(
                        withBlockOverride(number, first),
                        withBlockOverride(time, first),
                        withBlockOverride(number, second),
                        number))
                .build());

        assertThat(result)
                .hasSize(4)
                .allSatisfy(action -> assertThat(action.getError()).isNull());
        assertThat(result)
                .extracting(action -> decodedUint256(action.getOutput()))
                .containsExactly(
                        BigInteger.valueOf(80L),
                        BigInteger.valueOf(firstTime),
                        BigInteger.valueOf(90L),
                        BigInteger.valueOf(recordFile.getIndex()));
    }

    @Test
    void processTraceCallKeepsTheBundleBlockOverrideAfterARevert() {
        final var evmCodes = testWeb3jService.deploy(EvmCodes::deploy);
        final var ethCall = testWeb3jService.deploy(EthCall::deploy);
        final var recordFile = publishFixtureBlock();
        final var override = BlockOverride.builder().blockNumber(80L).build();
        final var number = getContractExecutionParameters(evmCodes.call_getBlockNumber(), evmCodes);
        final var revert = getContractExecutionParameters(ethCall.send_testRevert(), ethCall);

        // The reverted transaction is rolled back in the middle of the overridden bundle, and at its end.
        final var result = trace(TraceCall.builder()
                .transactions(List.of(
                        withBlockOverride(number, override),
                        withBlockOverride(revert, override),
                        withBlockOverride(number, override),
                        withBlockOverride(revert, override),
                        number))
                .build());

        assertThat(result).hasSize(5);
        for (final int reverted : new int[] {1, 3}) {
            assertThat(result.get(reverted).getError()).isEqualTo("execution reverted");
            assertThat(result.get(reverted).getRevertReason()).isEqualTo("Custom revert message");
        }
        assertThat(decodedUint256(result.get(0).getOutput())).isEqualTo(BigInteger.valueOf(80L));
        assertThat(decodedUint256(result.get(2).getOutput())).isEqualTo(BigInteger.valueOf(80L));
        assertThat(decodedUint256(result.get(4).getOutput())).isEqualTo(BigInteger.valueOf(recordFile.getIndex()));
    }

    @Test
    void processTraceCallKeepsTheTransactionIndexStateUnderABundleBlockOverride() {
        domainBuilder
                .recordFile()
                .customize(recordFile -> recordFile
                        .index(15L)
                        .consensusStart(1_000L)
                        .consensusEnd(3_000L)
                        .count(2L))
                .persist();
        persistTransaction(1_000L);
        persistTransaction(2_000L);
        final var block = BlockType.of("15");
        final var override =
                BlockOverride.builder().blockNumber(80L).time(1_793_619_195L).build();
        final var timestamps = new ArrayList<Optional<Long>>();
        final var lastBlockNumbers = new ArrayList<Long>();
        doAnswer(invocation -> {
                    timestamps.add(ContractCallContext.get().getTimestamp());
                    final BlockInfo blockInfo = mirrorNodeState
                            .getReadableStates(BlockRecordService.NAME)
                            .<BlockInfo>getSingleton(BLOCKS_STATE_ID)
                            .get();
                    lastBlockNumbers.add(blockInfo.lastBlockNumber());
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        trace(TraceCall.builder()
                .block(block)
                .transactionIndex(1)
                .transactions(
                        List.of(executionParameters(block, override), executionParameters(block, BlockOverride.EMPTY)))
                .build());

        // The override changes only the EVM block header. Both bundles read the state after the first transaction of
        // the block, and the bundle without an override sees the requested block again.
        assertThat(timestamps).containsExactly(Optional.of(1_001L), Optional.of(1_001L));
        assertThat(lastBlockNumbers).containsExactly(79L, 14L);
    }

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
        final var result = trace(traceCall(params, false));

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
        final var result = trace(traceCall(params, true));

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
        final var result = trace(traceCall(params, false));

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
        final var allActions = trace(traceCall(params, false));
        final var topCallOnly = trace(traceCall(params, true));

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

        final var allActions = trace(traceCall(params, false));
        final var topCallsOnly = trace(TraceCall.builder()
                .transactions(List.of(params, params, params))
                .onlyTopCall(true)
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

        final var result = trace(TraceCall.builder()
                .transactions(List.of(params))
                .stateOverrides(List.of(codeOverride, balanceOverride))
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

        final var result = trace(TraceCall.builder()
                .transactions(List.of(firstCall, secondCall))
                .stateOverrides(List.of(balanceOverride(firstAccount, 100L), balanceOverride(secondAccount, 200L)))
                .build());

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(action -> assertThat(action.getError()).isNull());
        assertThat(decodedUint256(result.get(0).getOutput())).isEqualTo(BigInteger.valueOf(100L));
        assertThat(decodedUint256(result.get(1).getOutput())).isEqualTo(BigInteger.valueOf(200L));
    }

    @Test
    void processTraceCallIncludesSha256Precompile() {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.call_calculateSHA256();
        final var params = getContractExecutionParameters(functionCall, contract);
        publishFixtureBlock();

        final var result = trace(traceCall(params, false));

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
                    ctx.getActionContext().addAction(remaining.removeFirst(), 0, 0L);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var result = trace(TraceCall.builder()
                .transactions(List.of(executionParameters(), executionParameters()))
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

        assertThatThrownBy(() -> trace(traceCallAtIndex(block, 6)))
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

        assertThatThrownBy(() -> trace(traceCallAtIndex(block, 3)))
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

        trace(TraceCall.builder()
                .block(BlockType.of("21"))
                .transactions(List.of(executionParameters(), executionParameters(), executionParameters()))
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
        assertThatThrownBy(() -> trace(traceCallAtIndex(BlockType.of("999999"), 1)))
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

        trace(TraceCall.builder()
                .block(BlockType.of(Long.toString(recordFile.getIndex())))
                .transactions(List.of(executionParameters(), executionParameters(), executionParameters()))
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

        trace(TraceCall.builder()
                .block(BlockType.of("16"))
                .transactions(List.of(
                        executionParameters(),
                        executionParameters(),
                        executionParameters(),
                        executionParameters(),
                        executionParameters(),
                        executionParameters(),
                        executionParameters()))
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

        trace(TraceCall.builder()
                .transactions(List.of(executionParameters(), executionParameters(), executionParameters()))
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
                    ctx.getActionContext().addAction(action("0x0" + snapshots.size(), TypeEnum.CALL), 0, 0L);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(TRANSACTION_GAS_LIMIT)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final var result = trace(TraceCall.builder()
                .block(BlockType.of(Long.toString(recordFile.getIndex())))
                .transactions(List.of(
                        executionParameters(), executionParameters(), executionParameters(), executionParameters()))
                .stateOverrides(List.of(stateOverride))
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
                    ContractCallContext.get().getActionContext().addAction(first, 0, 0L);
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
                () -> trace(TraceCall.builder()
                        .transactions(List.of(executionParameters(), executionParameters()))
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
                    ContractCallContext.get().getActionContext().addAction(first, 0, 0L);
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
                .transactions(List.of(executionParameters(), failedParams, skippedParams))
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
                .transactions(List.of(executionParameters(), executionParameters(), executionParameters()))
                .build();

        final long refundableGas = ContractCallContext.run(context -> {
            contractDebugService.processTraceCall(traceCall, context);
            return context.getRefundableGas();
        });

        assertThat(refundableGas).isEqualTo(3 * TRANSACTION_GAS_LIMIT * 2 / 3);
    }

    @Test
    void processTraceCallStopsAtTheDeadlineWithTheActionsSoFar() {
        final var first = action("0x01", TypeEnum.CALL);
        final var second = action("0x02", TypeEnum.CALL);
        final var calls = new AtomicInteger();
        // Each transaction uses a third of its gas. The tracer halts the second one at the deadline, and the executor
        // returns its result.
        doAnswer(invocation -> {
                    final var actionContext = ContractCallContext.get().getActionContext();
                    final long gasLimit = invocation.getArgument(1);
                    if (calls.incrementAndGet() == 1) {
                        actionContext.addAction(first, 0, gasLimit);
                    } else {
                        actionContext.addAction(second, 0, gasLimit);
                        actionContext.setTimedOut(true);
                    }
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(gasLimit / 3)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final long refundableGas = refundableGasAfterTimeout(threeTransactions(), first, second);

        assertThat(calls).hasValue(2);
        // The unused gas of the two transactions that ran, plus the whole gas limit of the one that did not.
        assertThat(refundableGas)
                .isEqualTo(2 * (TRANSACTION_GAS_LIMIT - TRANSACTION_GAS_LIMIT / 3) + TRANSACTION_GAS_LIMIT);
    }

    @Test
    void processTraceCallTurnsAFailureAfterTheDeadlineIntoATimeout() {
        final var first = action("0x01", TypeEnum.CALL);
        final var calls = new AtomicInteger();
        // The tracer halts the first transaction at the deadline, and the executor reports the halted frame as a
        // failure that used all of its gas.
        doAnswer(invocation -> {
                    calls.incrementAndGet();
                    final var actionContext = ContractCallContext.get().getActionContext();
                    final long gasLimit = invocation.getArgument(1);
                    actionContext.addAction(first, 0, gasLimit);
                    actionContext.setTimedOut(true);
                    throw new MirrorEvmTransactionException(
                            CONTRACT_EXECUTION_EXCEPTION.name(),
                            "TIMEOUT",
                            "0x",
                            new EvmTransactionResult(
                                    CONTRACT_EXECUTION_EXCEPTION,
                                    ContractFunctionResult.newBuilder()
                                            .gasUsed(gasLimit)
                                            .build()));
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());

        final long refundableGas = refundableGasAfterTimeout(threeTransactions(), first);

        assertThat(calls).hasValue(1);
        // A failure that used all of its gas refunds the configured percent of its gas limit, which is all of it here.
        assertThat(refundableGas).isEqualTo(3 * TRANSACTION_GAS_LIMIT);
    }

    @Test
    void processTraceCallTurnsAQueryTimeoutIntoATimeout() {
        final var first = action("0x01", TypeEnum.CALL);
        final var calls = stubQueryTimeoutAfterTheFirstTransaction(first);

        final long refundableGas = refundableGasAfterTimeout(threeTransactions(), first);

        assertThat(calls).hasValue(2);
        // The interrupted transaction has no result, so its whole gas limit is refundable like the one that did not
        // run.
        assertThat(refundableGas)
                .isEqualTo(TRANSACTION_GAS_LIMIT - TRANSACTION_GAS_LIMIT / 3 + 2 * TRANSACTION_GAS_LIMIT);
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
        doAnswer(invocation -> {
                    final var ctx = ContractCallContext.get();
                    final var actionContext = ctx.getActionContext();
                    assertThat(actionContext).isNotNull();
                    for (final var action : actions) {
                        actionContext.addAction(action, 0, 0L);
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

    /**
     * Runs {@code traceCall}, which must time out with {@code expectedActions}, and returns the refundable gas it
     * leaves in the context.
     */
    private long refundableGasAfterTimeout(final TraceCall traceCall, final ActionResponse... expectedActions) {
        return ContractCallContext.run(context -> {
            final var thrown = catchThrowableOfType(
                    TraceTimeoutException.class, () -> contractDebugService.processTraceCall(traceCall, context));
            assertThat(thrown).isNotNull();
            assertThat(thrown.getActionResponses()).containsExactly(expectedActions);
            return context.getRefundableGas();
        });
    }

    /**
     * The first transaction records {@code first} and uses a third of its gas. A database query of the next one passes
     * the deadline. Returns the count of executions.
     */
    private AtomicInteger stubQueryTimeoutAfterTheFirstTransaction(final ActionResponse first) {
        final var calls = new AtomicInteger();
        doAnswer(invocation -> {
                    final long gasLimit = invocation.getArgument(1);
                    if (calls.incrementAndGet() > 1) {
                        throw new QueryTimeoutException("Transaction timed out");
                    }
                    ContractCallContext.get().getActionContext().addAction(first, 0, gasLimit);
                    return new EvmTransactionResult(
                            SUCCESS,
                            ContractFunctionResult.newBuilder()
                                    .gasUsed(gasLimit / 3)
                                    .build());
                })
                .when(transactionExecutionService)
                .execute(any(), anyLong());
        return calls;
    }

    private TraceCall threeTransactions() {
        return TraceCall.builder()
                .transactions(List.of(executionParameters(), executionParameters(), executionParameters()))
                .build();
    }

    private ContractExecutionParameters executionParameters() {
        return executionParameters(BlockType.LATEST, BlockOverride.EMPTY);
    }

    private ContractExecutionParameters executionParameters(final BlockType block, final BlockOverride blockOverride) {
        return ContractExecutionParameters.builder()
                .block(block)
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
        trace(traceCallAtIndex(block, transactionIndex));
        assertThat(timestamps).hasSize(1);
        return timestamps.getFirst();
    }

    private TraceCall traceCallAtIndex(final BlockType block, final int transactionIndex) {
        return TraceCall.builder()
                .block(block)
                .transactions(List.of(executionParameters()))
                .transactionIndex(transactionIndex)
                .build();
    }

    private static StateOverride balanceOverride(final Address address, final long balance) {
        final var override = new StateOverride();
        override.setAddress(address.toHexString());
        override.setBalance(balance);
        return override;
    }

    private ApiProperties actionsApi() {
        // getApi() falls back to a shared default when an endpoint is not configured, which must not be changed.
        return web3Properties.getApi().computeIfAbsent(ACTIONS, _ -> new ApiProperties());
    }

    /** Posts a raw JSON body to the real actions endpoint with every trace feature enabled. */
    private ResultActions actionsCall(final String body) throws Exception {
        actionsApi().setEnabled(true);
        web3Properties.setEnableBlockOverrides(true);
        actionsProperties.setTraceMultipleTransactions(true);
        return mockMvc.perform(post(ACTIONS_URI)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String uint256Hex(final long value) {
        return Bytes32.leftPad(Bytes.ofUnsignedLong(value)).toHexString();
    }

    private RecordFile publishFixtureBlock() {
        // Fixture deployments happen after the genesis record file. Publish a block containing that state.
        final var recordFile = domainBuilder.recordFile().persist();
        recordFileLatestCacheManager.getCache(CACHE_NAME_RECORD_FILE_LATEST).invalidate();
        return recordFile;
    }

    private static ContractExecutionParameters withBlockOverride(
            final ContractExecutionParameters params, final BlockOverride blockOverride) {
        return ContractExecutionParameters.builder()
                .block(params.getBlock())
                .blockOverride(blockOverride)
                .callData(params.getCallData())
                .callType(params.getCallType())
                .gas(params.getGas())
                .gasPrice(params.getGasPrice())
                .isEstimate(params.isEstimate())
                .isStatic(params.isStatic())
                .receiver(params.getReceiver())
                .sender(params.getSender())
                .stateOverrides(params.getStateOverrides())
                .value(params.getValue())
                .build();
    }

    private static BigInteger decodedUint256(final String output) {
        return Bytes.fromHexString(output).toBigInteger();
    }

    private List<ActionResponse> trace(final TraceCall traceCall) {
        return ContractCallContext.run(ctx -> contractDebugService.processTraceCall(traceCall, ctx));
    }

    private static TraceCall traceCall(final ContractExecutionParameters params, final boolean onlyTopCall) {
        return TraceCall.builder()
                .transactions(List.of(params))
                .onlyTopCall(onlyTopCall)
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
