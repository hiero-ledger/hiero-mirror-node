// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.common.BlockOverrideAdvance;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.contracts.execution.OpcodesProcessingResult;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.ActionContext;
import org.hiero.mirror.web3.evm.contracts.execution.traceability.OpcodeContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
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
        return ContractCallContext.run(context -> {
            TraceCallState.bind(context, traceCall, recordFileService);
            context.setDeadlineMillis(
                    context.getStartTime() + traceCall.getTimeout().toMillis());

            final var actionContext = new ActionContext();
            context.setActionContext(actionContext);
            actionContext.setOnlyTopCall(traceCall.isOnlyTopCall());
            context.applyStateOverrides(traceCall.getStateOverrides());

            final var bundles = traceCall.getBundles();
            int started = 0;
            try {
                final var timestamp = TraceCallState.resolveStateTimestamp(context, traceCall, transactionRepository);
                if (timestamp != null) {
                    context.setTimestamp(Optional.of(timestamp));
                }
                final var recordFile = context.getRecordFile();
                BlockOverrideAdvance.Cursor cursor = null;
                for (int bundleIndex = 0; bundleIndex < bundles.size(); bundleIndex++) {
                    final var bundle = bundles.get(bundleIndex);
                    cursor = TraceCallState.blockCursor(recordFile, cursor, bundle, bundleIndex);
                    final var progress = executeBundle(context, actionContext, bundle, cursor, started);
                    started = progress.started();
                    final var failure = progress.failure();
                    if (failure != null) {
                        throw failure;
                    }
                }
                return actionResponse(context);
            } catch (final QueryTimeoutException _) {
                restoreUnstartedGas(bundles, started);
                throw new TraceTimeoutException(actionResponse(context));
            } catch (final RuntimeException exception) {
                restoreUnstartedGas(bundles, started);
                throw exception;
            }
        });
    }

    private BundleProgress executeBundle(
            final ContractCallContext context,
            final ActionContext actionContext,
            final TraceBundle bundle,
            final BlockOverrideAdvance.Cursor cursor,
            final int started) {
        context.evictCachedState(BLOCKS_STATE_ID);
        context.evictCachedState(BLOCK_STREAM_INFO_STATE_ID);
        context.applyBlockOverride(cursor.number(), cursor.timeSeconds());

        int executed = started;
        for (final var transaction : bundle.getTransactions()) {
            actionContext.beginCall();
            final var snapshot = context.captureCaches();
            RuntimeException failure = null;
            try {
                if (!callContract(transaction, context).isSuccessful()) {
                    context.restoreCaches(snapshot);
                }
            } catch (final QueryTimeoutException _) {
                context.restoreCaches(snapshot);
                failure = new TraceTimeoutException(actionResponse(context));
            } catch (final MirrorEvmTransactionException exception) {
                context.restoreCaches(snapshot);
                failure = actionContext.isTimedOut() ? new TraceTimeoutException(actionResponse(context)) : exception;
            } catch (final RuntimeException exception) {
                context.restoreCaches(snapshot);
                failure = exception;
            } finally {
                // callContract's finally already returned unused gas. Count the transaction so a later abort does not
                // return that gas again.
                executed++;
            }
            if (failure != null) {
                return new BundleProgress(executed, failure);
            }
            if (actionContext.isTimedOut()) {
                return new BundleProgress(executed, new TraceTimeoutException(actionResponse(context)));
            }
        }
        return new BundleProgress(executed, null);
    }

    private void restoreUnstartedGas(final List<TraceBundle> bundles, final int started) {
        int index = 0;
        for (final var bundle : bundles) {
            for (final var transaction : bundle.getTransactions()) {
                if (index >= started) {
                    restoreReservedGas(transaction.getGas());
                }
                index++;
            }
        }
    }

    private List<ActionResponse> actionResponse(final ContractCallContext context) {
        return List.copyOf(context.getActionContext().getActions());
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

    /** Transactions started so far, and the failure that stopped the bundle when execution did not finish. */
    private record BundleProgress(int started, @Nullable RuntimeException failure) {}
}
