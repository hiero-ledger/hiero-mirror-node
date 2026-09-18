// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static com.hedera.services.stream.proto.ContractActionType.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.rest.model.ActionResponse.TypeEnum;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.controller.OpcodesProperties;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.service.model.TraceRequest;
import org.hiero.mirror.web3.utils.HexUtils;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hiero.mirror.web3.web3j.generated.EthCall;
import org.hiero.mirror.web3.web3j.generated.EvmCodes;
import org.hiero.mirror.web3.web3j.generated.InternalCaller;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ContractDebugServiceTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration INTEGRATION_TIMEOUT = Duration.ofSeconds(10);
    private static final int NUM_DEPTHS = 4;
    private static final int ACTIONS_PER_DEPTH = 2;

    @MockitoBean
    private ContractActionRepository contractActionRepository;

    @Test
    void processOpcodeCallMapsRevertedActionsToCorrectDepths() {
        // Given – one unique revert message per action, 8 total
        setOpcodeEndpoint();
        stubNestedRevertSimulation();
        final var timestamp = domainBuilder.timestamp();
        final var revertedActions = buildRevertedActions(timestamp);

        when(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(timestamp))
                .thenReturn(revertedActions);

        final var opcodeContext = new OpcodeContext(
                new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false),
                0,
                new OpcodesProperties());

        final var params = ContractDebugParameters.builder()
                .block(BlockType.LATEST)
                .callData(new byte[0])
                .consensusTimestamp(timestamp)
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
        final var result = contractDebugService.processTraceCall(
                List.of(new TraceRequest(params, false, INTEGRATION_TIMEOUT, null)));

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
        final var result =
                contractDebugService.processTraceCall(List.of(new TraceRequest(params, true, DEFAULT_TIMEOUT, null)));

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

        // When
        final var result = contractDebugService.processTraceCall(
                List.of(new TraceRequest(params, false, INTEGRATION_TIMEOUT, null)));

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

        // When
        final var allActions = contractDebugService.processTraceCall(
                List.of(new TraceRequest(params, false, INTEGRATION_TIMEOUT, null)));
        final var topCallOnly = contractDebugService.processTraceCall(
                List.of(new TraceRequest(params, true, INTEGRATION_TIMEOUT, null)));

        // Then
        assertThat(allActions).hasSize(1);
        assertThat(topCallOnly).hasSize(1);
        assertThat(allActions.getFirst().getCalls()).isNotEmpty();
        assertThat(topCallOnly.getFirst().getCalls()).isNullOrEmpty();
        assertThat(topCallOnly.getFirst().getTo())
                .isEqualTo(allActions.getFirst().getTo());
    }

    @Test
    void processTraceCallAppliesRequestedTimeout() {
        final var deadlineWindow = new AtomicLong();
        stubActionsAndCaptureDeadline(deadlineWindow, action("0x02", TypeEnum.CALL));

        contractDebugService.processTraceCall(
                List.of(new TraceRequest(executionParameters(), false, Duration.ofSeconds(2), null)));

        assertThat(deadlineWindow).hasValue(Duration.ofSeconds(2).toMillis());
    }

    @Test
    void processTraceCallIncludesSha256Precompile() {
        final var contract = testWeb3jService.deploy(EvmCodes::deploy);
        final var functionCall = contract.call_calculateSHA256();
        final var params = getContractExecutionParameters(functionCall, contract);

        final var result = contractDebugService.processTraceCall(
                List.of(new TraceRequest(params, false, INTEGRATION_TIMEOUT, null)));

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

        contractDebugService.processTraceCall(
                List.of(new TraceRequest(executionParameters(), false, DEFAULT_TIMEOUT, override)));

        assertThat(captured).hasValue(256L);
    }

    @Test
    void processTraceCallAppliesBlockOverrideTime() {
        final var captured = new AtomicReference<Long>();
        stubActionsAndCaptureContext(
                ctx -> captured.set(ctx.getBlockOverrideTimeNanos()), action("0x02", TypeEnum.CALL));
        final var override = new BlockOverride();
        override.setTime("0x65f9e0c0");

        contractDebugService.processTraceCall(
                List.of(new TraceRequest(executionParameters(), false, DEFAULT_TIMEOUT, override)));

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

        final var result = contractDebugService.processTraceCall(List.of(
                new TraceRequest(executionParameters(), false, DEFAULT_TIMEOUT, null),
                new TraceRequest(executionParameters(), true, DEFAULT_TIMEOUT, null)));

        assertThat(result).containsExactly(first, second);
        assertThat(remaining).isEmpty();
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

    private ActionResponse action(final String from, final TypeEnum type) {
        return new ActionResponse().from(from).to("0x03").type(type).gas("0x0").gasUsed("0x0");
    }
}
