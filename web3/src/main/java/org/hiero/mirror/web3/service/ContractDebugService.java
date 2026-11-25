// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.utils.Constants.MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Named;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import org.hiero.mirror.common.domain.transaction.RecordFile;
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
import org.hiero.mirror.web3.service.model.ContractExecutionParameters;
import org.hiero.mirror.web3.service.model.EvmTransactionResult;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.throttle.ThrottleProperties;
import org.hiero.mirror.web3.utils.Suppliers;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.validation.annotation.Validated;

@CustomLog
@Named
@Validated
public class ContractDebugService extends ContractCallService {

    private final ContractActionRepository contractActionRepository;
    private final TransactionRepository transactionRepository;

    public ContractDebugService(
            ContractActionRepository contractActionRepository,
            RecordFileService recordFileService,
            TransactionRepository transactionRepository,
            MeterRegistry meterRegistry,
            EvmProperties evmProperties,
            ThrottleProperties throttleProperties,
            TransactionExecutionService transactionExecutionService) {
        super(meterRegistry, recordFileService, evmProperties, throttleProperties, transactionExecutionService);
        this.contractActionRepository = contractActionRepository;
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

    /**
     * Runs every transaction in order in {@code context}, which must be the bound {@link ContractCallContext}. Each
     * transaction sees the state changes of the successful transactions before it, while a failed or reverted
     * transaction's changes are rolled back, except for its fee charge. A timeout returns the actions collected so far.
     * Afterwards {@code context} holds the {@link ContractCallContext#getRefundableGas() refundable gas} of the whole
     * trace: the unused gas of each transaction that ran, plus the whole gas limit of each transaction that did not.
     *
     * @param traceCall the trace to run
     * @param context   the bound contract call context
     */
    public List<ActionResponse> processTraceCall(final TraceCall traceCall, final ContractCallContext context) {
        final var transactions = traceCall.getTransactions();
        // The reserved gas of the transactions that have not run yet. None of it has been used, so all of it is
        // refundable. Each transaction takes its gas limit out of it before it runs.
        long unstartedGas = 0L;
        for (final var transaction : transactions) {
            unstartedGas += transaction.getGas();
        }
        // The gas the transactions that ran did not use. Gas they consumed is never refunded.
        long unusedGas = 0L;

        // One record-file supplier for the whole trace, so every transaction runs in the same block.
        context.setApi(ACTIONS);
        context.setBlockSupplier(Suppliers.memoize(() -> recordFileService
                .findByBlockType(traceCall.getBlock())
                .orElseThrow(BlockNumberNotFoundException::new)));
        context.applyStateOverrides(traceCall.getStateOverrides());
        final var actionContext = new ActionContext();
        actionContext.setOnlyTopCall(traceCall.isOnlyTopCall());
        context.setActionContext(actionContext);

        try {
            // A single transaction at latest reads current state like eth_call. Otherwise every transaction reads
            // one pinned state, so a record file imported mid-request cannot change it.
            if (!BlockType.LATEST.equals(traceCall.getBlock()) || transactions.size() > 1) {
                context.setTimestamp(Optional.of(resolveStateTimestamp(context.getRecordFile(), traceCall)));
            }
            for (final var transaction : transactions) {
                final long gasLimit = transaction.getGas();
                unstartedGas -= gasLimit;
                // The execution replaces this with the gas it did not use, so a transaction that fails before the
                // EVM runs keeps its whole gas limit refundable.
                context.setRefundableGas(gasLimit);
                try {
                    executeTransaction(context, actionContext, transaction);
                } finally {
                    unusedGas += context.getRefundableGas();
                }
            }
            return actionContext.getActions();
        } catch (final QueryTimeoutException _) {
            throw new TraceTimeoutException(actionContext.getActions());
        } catch (final MirrorEvmTransactionException exception) {
            if (actionContext.isTimedOut()) {
                throw new TraceTimeoutException(actionContext.getActions());
            }
            throw exception;
        } finally {
            context.setRefundableGas(unusedGas + unstartedGas);
        }
    }

    @Override
    protected void validateResult(final EvmTransactionResult txnResult, final CallServiceParameters params) {
        // A trace returns failed transactions in its 200 response, so they are expected and not worth a warning.
        if (ContractCallContext.get().getActionContext() != null) {
            return;
        }
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

    /**
     * Runs one transaction against the trace's record-file supplier, with the block override of its bundle. The
     * transaction executor commits a successful transaction's state changes into the request caches of
     * {@code context}, so later transactions see them. It rolls back the state changes of a failed or reverted
     * transaction, but keeps its fee charge, as a consensus node does.
     */
    private void executeTransaction(
            final ContractCallContext context,
            final ActionContext actionContext,
            final ContractExecutionParameters transaction) {
        actionContext.beginCall();
        context.setBlockOverride(transaction.getBlockOverride());
        context.setCallServiceParameters(transaction);
        doProcessCall(transaction, transaction.getGas(), false);
        if (actionContext.isTimedOut()) {
            throw new TraceTimeoutException(actionContext.getActions());
        }
    }

    /**
     * State timestamp for the whole trace. {@code transaction_index} {@code -1}, or any value at {@code latest}, reads
     * the end of the block. {@code 0} reads the start of the block, one nanosecond before its first transaction. A
     * positive {@code N} reads one nanosecond after the Nth top-level {@code ContractCall}, {@code ContractCreate}, or
     * {@code EthereumTransaction} of the block, ordered by consensus timestamp. A scheduled execution may fall after
     * the record file's consensus end, within the scheduled consensus window.
     */
    private long resolveStateTimestamp(final RecordFile recordFile, final TraceCall traceCall) {
        final int transactionIndex = traceCall.getTransactionIndex();
        if (transactionIndex == -1 || BlockType.LATEST.equals(traceCall.getBlock())) {
            return recordFile.getConsensusEnd();
        }
        final long consensusStart = recordFile.getConsensusStart();
        if (transactionIndex == 0) {
            return consensusStart - 1;
        }
        // transaction_index counts from 1, while the query position counts from 0.
        final var transactionTimestamp = transactionRepository.findConsensusTimestampAtPosition(
                consensusStart,
                recordFile.getConsensusEnd(),
                consensusStart + MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS,
                transactionIndex - 1);
        if (transactionTimestamp == null) {
            throw new InvalidParametersException("transaction_index exceeds the number of transactions in the block");
        }
        return transactionTimestamp + 1;
    }
}
