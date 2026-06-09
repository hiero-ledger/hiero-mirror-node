// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.services.stream.proto.ContractAction.ResultDataCase.REVERT_REASON;
import static com.hedera.services.stream.proto.ContractActionType.SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hiero.mirror.common.domain.contract.ContractAction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.rest.model.Opcode;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.common.TransactionIdParameter;
import org.hiero.mirror.web3.convert.BytesDecoder;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.OpcodeRequest;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for {@link ContractDebugService#processOpcodeCall}. Validates that reverted system-contract
 * actions from historical DB records are correctly grouped by call depth and mapped to the returned opcode list at the
 * expected positions.
 */
class ContractDebugServiceTest extends AbstractContractCallServiceOpcodeTracerTest {

    private static final int NUM_DEPTHS = 4;
    private static final int ACTIONS_PER_DEPTH = 2;

    /**
     * Replaces the real {@link ContractActionRepository} so that we can return a controlled set of reverted
     * {@link ContractAction} records without touching the database.
     */
    @MockitoBean
    private ContractActionRepository contractActionRepository;

    /**
     * Overrides the parent spy answer for {@code TransactionExecutionService.execute()} to simulate four nested EVM
     * frames, each containing two reverted system-contract calls.  The doAnswer runs after
     * {@link ContractDebugService#processOpcodeCall} has already called {@code setActions()}, so the
     * {@link OpcodeContext} is fully populated with the mocked actions when we consume them here.
     */
    @BeforeEach
    void setUpNestedRevertSimulation() {
        setOpcodeEndpoint();
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

    @Test
    void processOpcodeCallMapsRevertedActionsToCorrectDepths() {
        // Given – one unique revert message per action, 2 actions × 4 depths = 8 total
        final var timestamp = domainBuilder.timestamp();
        final var revertedActions = buildRevertedActions(timestamp);

        when(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(timestamp))
                .thenReturn(revertedActions);

        final var opcodeContext = new OpcodeContext(
                new OpcodeRequest(new TransactionIdParameter(EntityId.EMPTY, Instant.EPOCH), false, false, false), 0);

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

        // Then – exactly 8 opcodes are returned, one per reverted action
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
}
