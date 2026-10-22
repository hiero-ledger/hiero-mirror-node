// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_STATE_ID;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCKS_STATE_ID;
import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.utils.Constants.MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.ActionResponse;
import org.hiero.mirror.web3.common.BlockOverrideAdvance;
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
import org.hiero.mirror.web3.viewmodel.BlockType;
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
    private static final String TRANSACTION_INDEX_ERROR = "Invalid transaction_index";

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
            bindTrace(ctx, traceCall);
            ctx.setDeadlineMillis(ctx.getStartTime() + traceCall.getTimeout().toMillis());

            final var actionContext = ActionContext.builder().build();
            ctx.setActionContext(actionContext);
            actionContext.setOnlyTopCall(traceCall.isOnlyTopCall());
            final var stateOverride = traceCall.getStateOverride();
            ctx.applyStateOverrides(stateOverride == null ? List.of() : List.of(stateOverride));

            final var bundles = traceCall.getBundles();
            final var started = new int[1];
            try {
                final var timestamp = resolveStateTimestamp(ctx, traceCall);
                if (timestamp != null) {
                    ctx.setTimestamp(Optional.of(timestamp));
                }
                final var recordFile = ctx.getRecordFile();
                BlockOverrideAdvance.Cursor cursor = null;
                for (var bundleIndex = 0; bundleIndex < bundles.size(); bundleIndex++) {
                    final var bundle = bundles.get(bundleIndex);
                    cursor = blockCursor(recordFile, cursor, bundle, bundleIndex);
                    executeBundle(ctx, actionContext, bundle, cursor, started);
                }
                return actionResponse(ctx);
            } catch (final QueryTimeoutException _) {
                restoreUnstartedGas(bundles, started[0]);
                throw new TraceTimeoutException(actionResponse(ctx));
            } catch (final RuntimeException exception) {
                restoreUnstartedGas(bundles, started[0]);
                throw exception;
            }
        });
    }

    /**
     * Rejects a bad {@code transaction_index} or a block cursor older than the bound record file before any rate token
     * is taken. The first bundle's omitted header fields come from that record file's block number and consensus start.
     */
    public void validateTrace(final @Valid TraceCall traceCall) {
        ContractCallContext.run(ctx -> {
            bindTrace(ctx, traceCall);
            resolveStateTimestamp(ctx, traceCall);
            final var recordFile = ctx.getRecordFile();
            BlockOverrideAdvance.Cursor cursor = null;
            final var bundles = traceCall.getBundles();
            for (var bundleIndex = 0; bundleIndex < bundles.size(); bundleIndex++) {
                cursor = blockCursor(recordFile, cursor, bundles.get(bundleIndex), bundleIndex);
            }
            return null;
        });
    }

    private void executeBundle(
            final ContractCallContext ctx,
            final ActionContext actionContext,
            final TraceBundle bundle,
            final BlockOverrideAdvance.Cursor cursor,
            final int[] started) {
        ctx.evictCachedState(BLOCKS_STATE_ID);
        ctx.evictCachedState(BLOCK_STREAM_INFO_STATE_ID);
        ctx.setBlockOverrideNumber(cursor.number());
        ctx.setBlockOverrideTimeNanos(
                cursor.timeSeconds() == null ? null : DomainUtils.convertToNanosMax(cursor.timeSeconds(), 0L));

        for (final var transaction : bundle.getTransactions()) {
            actionContext.beginCall();
            final var snapshot = ctx.captureCaches();
            try {
                final var result = callContract(transaction, ctx);
                if (result.isSuccessful()) {
                    ctx.retainDroppedWrites(snapshot);
                } else {
                    ctx.restoreCaches(snapshot);
                }
            } catch (final QueryTimeoutException _) {
                ctx.restoreCaches(snapshot);
                throw new TraceTimeoutException(actionResponse(ctx));
            } catch (final MirrorEvmTransactionException exception) {
                ctx.restoreCaches(snapshot);
                if (actionContext.isTimedOut()) {
                    throw new TraceTimeoutException(actionResponse(ctx));
                }
                throw exception;
            } catch (final RuntimeException exception) {
                ctx.restoreCaches(snapshot);
                throw exception;
            } finally {
                // callContract's finally already returned unused gas. Count the transaction so a later abort does not
                // return that gas again.
                started[0]++;
            }
            if (actionContext.isTimedOut()) {
                throw new TraceTimeoutException(actionResponse(ctx));
            }
        }
    }

    /**
     * State timestamp for the whole trace. {@code 0} is the beginning of the block, one nanosecond before its first
     * transaction. {@code -1} is after every transaction in the block: a past block uses that record file's consensus
     * end, and {@code latest} keeps current state. A positive index is the consensus timestamp of that top-level
     * {@code ContractCall}, {@code ContractCreate}, or {@code EthereumTransaction}. A scheduled execution may fall after
     * the record file's consensus end, within the scheduled consensus window.
     * Child rows, duplicates, and other transaction types are skipped. The
     * record file comes from the supplier installed for the whole trace. State reads are inclusive, so the consensus
     * end includes the block's last transaction. {@code block_override} changes only the EVM block header.
     */
    private @Nullable Long resolveStateTimestamp(final ContractCallContext ctx, final TraceCall traceCall) {
        final int transactionIndex = traceCall.getTransactionIndex();
        if (transactionIndex < -1) {
            throw new InvalidParametersException(TRANSACTION_INDEX_ERROR);
        }
        // latest has no closed block end. Current state is the state after every imported transaction.
        if (transactionIndex == -1 && traceCall.getBlock() == BlockType.LATEST) {
            return null;
        }
        final var recordFile = ctx.getRecordFile();
        final var consensusStart = recordFile.getConsensusStart();
        final var consensusEnd = recordFile.getConsensusEnd();
        if (consensusStart == null || consensusEnd == null) {
            throw transactionIndexOutOfRange();
        }
        if (transactionIndex == -1) {
            return consensusEnd;
        }
        if (transactionIndex == 0) {
            try {
                return Math.subtractExact(consensusStart, 1L);
            } catch (final ArithmeticException _) {
                throw new InvalidParametersException(TRANSACTION_INDEX_ERROR);
            }
        }
        final long scheduledConsensusTimestampEnd = scheduledConsensusTimestampEnd(consensusStart);
        return transactionRepository
                .findConsensusTimestampAtPosition(
                        consensusStart, consensusEnd, scheduledConsensusTimestampEnd, transactionIndex - 1)
                .orElseThrow(this::transactionIndexOutOfRange);
    }

    private void restoreUnstartedGas(final List<TraceBundle> bundles, final int started) {
        var index = 0;
        for (final var bundle : bundles) {
            for (final var transaction : bundle.getTransactions()) {
                if (index >= started) {
                    restoreReservedGas(transaction.getGas());
                }
                index++;
            }
        }
    }

    private void bindTrace(final ContractCallContext ctx, final TraceCall traceCall) {
        ctx.setApi(ACTIONS);
        ctx.setBlockSupplier(Suppliers.memoize(() -> recordFileService
                .findByBlockType(traceCall.getBlock())
                .orElseThrow(BlockNumberNotFoundException::new)));
    }

    private static BlockOverrideAdvance.Cursor blockCursor(
            final RecordFile recordFile,
            final BlockOverrideAdvance.@Nullable Cursor previous,
            final TraceBundle bundle,
            final int bundleIndex) {
        if (bundleIndex == 0) {
            final long recordNumber = recordFile.getIndex() == null ? 0L : recordFile.getIndex();
            return BlockOverrideAdvance.first(recordNumber, headerSeconds(recordFile), bundle.getBlockOverride());
        }
        final var prior = Objects.requireNonNull(previous);
        return BlockOverrideAdvance.next(prior.number(), prior.timeSeconds(), bundle.getBlockOverride(), bundleIndex);
    }

    /** EVM block timestamp seconds for a record file. Uses consensus start, the same header time as eth_call. */
    private static long headerSeconds(final RecordFile recordFile) {
        final var consensusStart = recordFile.getConsensusStart();
        return consensusStart == null ? 0L : Math.floorDiv(consensusStart, DomainUtils.NANOS_PER_SECOND);
    }

    private static long scheduledConsensusTimestampEnd(final long consensusStart) {
        try {
            return Math.addExact(consensusStart, MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS);
        } catch (final ArithmeticException _) {
            // The block starts so late that the scheduled window does not fit in a signed timestamp.
            return Long.MAX_VALUE;
        }
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
