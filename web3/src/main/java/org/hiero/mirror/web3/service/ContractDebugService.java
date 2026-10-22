// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.exception.MirrorEvmTransactionException;
import org.hiero.mirror.web3.exception.TraceTimeoutException;
import org.hiero.mirror.web3.repository.ContractActionRepository;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.service.model.ContractDebugParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.TraceBundle;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.throttle.ThrottleManager;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.utils.Suppliers;
import org.hiero.mirror.web3.viewmodel.BlockOverride;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.validation.annotation.Validated;

@CustomLog
@Named
@Validated
public class ContractDebugService extends ContractCallService {
    private final ContractActionRepository contractActionRepository;
    private final RecordFileService recordFileService;
    private final TransactionRepository transactionRepository;

    @SuppressWarnings("java:S107")
    public ContractDebugService(
            ContractActionRepository contractActionRepository,
            RecordFileService recordFileService,
            TransactionRepository transactionRepository,
            ThrottleManager throttleManager,
            ThrottleProperties throttleProperties,
            MeterRegistry meterRegistry,
            EvmProperties evmProperties,
            TransactionExecutionService transactionExecutionService) {
        super(
                throttleManager,
                throttleProperties,
                meterRegistry,
                recordFileService,
                evmProperties,
                transactionExecutionService);
        this.contractActionRepository = contractActionRepository;
        this.recordFileService = recordFileService;
        this.transactionRepository = transactionRepository;
    }

    public OpcodesProcessingResult processOpcodeCall(
            final @Valid ContractDebugParameters params, final OpcodeContext opcodeContext) {
        final var ctx = ContractCallContext.get();
        ctx.setTimestamp(Optional.of(params.getConsensusTimestamp() - 1));
        ctx.setOpcodeContext(opcodeContext);
        ctx.getOpcodeContext()
                .setActions(contractActionRepository.findFailedSystemActionsByConsensusTimestamp(
                        params.getConsensusTimestamp(), params.getPayerAccountId()));
        final var ethCallTxnResult = callContract(params, ctx);
        return new OpcodesProcessingResult(
                ethCallTxnResult, params.getReceiver(), ctx.getOpcodeContext().getOpcodes());
    }

    public List<ActionResponse> processTraceCall(final @Valid TraceCall traceCall) {
        return ContractCallContext.run(ctx -> {
            ctx.setApi(ACTIONS);
            ctx.setBlockSupplier(Suppliers.memoize(() -> recordFileService
                    .findByBlockType(traceCall.getBlock())
                    .orElseThrow(BlockNumberNotFoundException::new)));
            ctx.setDeadlineMillis(ctx.getStartTime() + traceCall.getTimeout().toMillis());

            applyStateTimestamp(ctx, traceCall);
            final var actionContext = ActionContext.builder().build();
            ctx.setActionContext(actionContext);
            actionContext.setOnlyTopCall(traceCall.isOnlyTopCall());
            final var stateOverride = traceCall.getStateOverride();
            ctx.applyStateOverrides(stateOverride == null ? List.of() : List.of(stateOverride));

            final var bundles = traceCall.getBundles();
            for (final var bundle : bundles) {
                executeBundle(ctx, actionContext, bundle);
            }
            return actionResponse(ctx);
        });
    }

    private void executeBundle(
            final ContractCallContext ctx, final ActionContext actionContext, final TraceBundle bundle) {
        ctx.evictCachedState(BLOCKS_STATE_ID);
        ctx.evictCachedState(BLOCK_STREAM_INFO_STATE_ID);
        applyBlockOverride(ctx, bundle.getBlockOverride());

        final var transactions = bundle.getTransactions();
        for (final var transaction : transactions) {
            actionContext.beginCall();
            try {
                callContract(transaction, ctx);
            } catch (final QueryTimeoutException _) {
                throw new TraceTimeoutException(actionResponse(ctx));
            } catch (final MirrorEvmTransactionException e) {
                if (actionContext.isTimedOut()) {
                    throw new TraceTimeoutException(actionResponse(ctx));
                }
                throw e;
            }
            if (actionContext.isTimedOut()) {
                throw new TraceTimeoutException(actionResponse(ctx));
            }
        }
    }

    private void applyBlockOverride(final ContractCallContext ctx, final @Nullable BlockOverride override) {
        final Long previousNumber = ctx.getBlockOverrideNumber();
        final Long previousTime = ctx.getBlockOverrideTimeNanos();
        if (override == null) {
            if (ctx.hasBlockOverride()) {
                ctx.advanceBlockOverride();
            }
            return;
        }
        ctx.applyBlockOverride(override);
        try {
            if (ctx.getBlockOverrideNumber() == null && previousNumber != null) {
                ctx.setBlockOverrideNumber(Math.addExact(previousNumber, 1L));
            }
            if (ctx.getBlockOverrideTimeNanos() == null && previousTime != null) {
                ctx.setBlockOverrideTimeNanos(Math.addExact(previousTime, DomainUtils.convertToNanosMax(2L, 0L)));
            }
        } catch (final ArithmeticException _) {
            throw new InvalidParametersException("Invalid block_override: block context overflow");
        }
    }

    /**
     * Starts the first bundle at the consensus timestamp of {@code transaction_index} in the request block. The index is
     * pointing to the n-th transactions of the block, so for example 5 is the state after the first five transactions.
     * {@code -1} uses the block state directly. Each later bundle keeps that base timestamp and increases it by 2
     * seconds. A bundle that sets {@code block_override.time} uses that timestamp for its own transactions. The next
     * bundle continues from the base, not from the override. Transactions in one bundle share one timestamp.
     */
    private void applyStateTimestamp(final ContractCallContext ctx, final TraceCall traceCall) {
        final int transactionIndex = traceCall.getTransactionIndex();
        if (transactionIndex == -1) {
            return;
        }
        if (transactionIndex < 1) {
            throw new InvalidParametersException("Invalid transaction_index");
        }
        final var recordFile =
                recordFileService.findByBlockType(traceCall.getBlock()).orElseThrow(BlockNumberNotFoundException::new);
        final var consensusStart = recordFile.getConsensusStart();
        final var consensusEnd = recordFile.getConsensusEnd();
        if (consensusStart == null || consensusEnd == null) {
            throw transactionIndexOutOfRange();
        }
        final var stateTimestamp = transactionRepository
                .findConsensusTimestampAtPosition(consensusStart, consensusEnd, transactionIndex - 1)
                .orElseThrow(this::transactionIndexOutOfRange);
        ctx.setTimestamp(Optional.of(stateTimestamp));
    }

    private InvalidParametersException transactionIndexOutOfRange() {
        return new InvalidParametersException("transaction_index exceeds the number of transactions in the block");
    }

    private List<ActionResponse> actionResponse(final ContractCallContext ctx) {
        return List.copyOf(ctx.getActionContext().getActions());
    }

    @Override
    protected void validateResult(final EvmTransactionResult txnResult, final CallServiceParameters params) {
        try {
            super.validateResult(txnResult, params);
        } catch (MirrorEvmTransactionException e) {
            log.warn(
                    "Transaction failed with status: {}, detail: {}, revertReason: {}",
                    txnResult.responseCodeEnum(),
                    e.getDetail(),
                    e.getData());
        }
    }
}
