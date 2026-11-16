// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.service;

import static org.hiero.mirror.web3.ApiEndpointName.ACTIONS;
import static org.hiero.mirror.web3.utils.Constants.MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS;

import java.util.Objects;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.common.BlockOverrideAdvance;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.exception.BlockNumberNotFoundException;
import org.hiero.mirror.web3.exception.InvalidParametersException;
import org.hiero.mirror.web3.repository.TransactionRepository;
import org.hiero.mirror.web3.service.model.TraceBundle;
import org.hiero.mirror.web3.service.model.TraceCall;
import org.hiero.mirror.web3.utils.Suppliers;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.jspecify.annotations.Nullable;

/** Record-file binding, state timestamp, and block cursor shared by trace validation and execution. */
public final class TraceCallState {

    private static final String TRANSACTION_INDEX_ERROR = "Invalid transaction_index";

    private TraceCallState() {}

    public static void bind(
            final ContractCallContext context, final TraceCall traceCall, final RecordFileService recordFileService) {
        context.setApi(ACTIONS);
        context.setBlockSupplier(Suppliers.memoize(() -> recordFileService
                .findByBlockType(traceCall.getBlock())
                .orElseThrow(BlockNumberNotFoundException::new)));
    }

    public static BlockOverrideAdvance.Cursor blockCursor(
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

    /**
     * State timestamp for the whole trace. The record file comes from the request's {@code block} through the supplier
     * installed for the whole trace. When {@code block} is {@code latest}, {@code transaction_index} has no effect and
     * the trace reads state after every transaction of the latest record file. {@code -1} is after every transaction in
     * the block, at the record file's consensus end. {@code 0} is the beginning of the block, one nanosecond before its
     * first transaction. A positive index {@code N} is one nanosecond after the consensus timestamp of the block's Nth
     * top-level {@code ContractCall}, {@code ContractCreate}, or {@code EthereumTransaction}, so the state includes that
     * transaction. A scheduled execution may fall after the record file's consensus end, within the scheduled consensus
     * window. Child rows, duplicates, and other transaction types are skipped. {@code block_override} changes only the
     * EVM block header.
     */
    public static @Nullable Long resolveStateTimestamp(
            final ContractCallContext context,
            final TraceCall traceCall,
            final TransactionRepository transactionRepository) {
        final int transactionIndex = traceCall.getTransactionIndex();
        if (transactionIndex < -1) {
            throw new InvalidParametersException(TRANSACTION_INDEX_ERROR);
        }
        final var recordFile = context.getRecordFile();
        final var consensusStart = recordFile.getConsensusStart();
        final var consensusEnd = recordFile.getConsensusEnd();
        if (consensusStart == null || consensusEnd == null) {
            throw transactionIndexOutOfRange();
        }
        if (transactionIndex == -1 || BlockType.LATEST.equals(traceCall.getBlock())) {
            return consensusEnd;
        }
        if (transactionIndex == 0) {
            return offset(consensusStart, -1L);
        }
        final long scheduledConsensusTimestampEnd = scheduledConsensusTimestampEnd(consensusStart);
        final long transactionTimestamp = transactionRepository
                .findConsensusTimestampAtPosition(
                        consensusStart, consensusEnd, scheduledConsensusTimestampEnd, transactionIndex)
                .orElseThrow(TraceCallState::transactionIndexOutOfRange);
        return offset(transactionTimestamp, 1L);
    }

    /** EVM block timestamp seconds for a record file. Uses consensus start, the same header time as eth_call. */
    private static long headerSeconds(final RecordFile recordFile) {
        final var consensusStart = recordFile.getConsensusStart();
        return consensusStart == null ? 0L : Math.floorDiv(consensusStart, DomainUtils.NANOS_PER_SECOND);
    }

    private static long offset(final long timestamp, final long nanos) {
        try {
            return Math.addExact(timestamp, nanos);
        } catch (final ArithmeticException _) {
            throw new InvalidParametersException(TRANSACTION_INDEX_ERROR);
        }
    }

    private static long scheduledConsensusTimestampEnd(final long consensusStart) {
        try {
            return Math.addExact(consensusStart, MAX_SCHEDULED_TRANSACTION_CONSENSUS_TIMESTAMP_RANGE_NS);
        } catch (final ArithmeticException _) {
            // The block starts so late that the scheduled window does not fit in a signed timestamp.
            return Long.MAX_VALUE;
        }
    }

    private static InvalidParametersException transactionIndexOutOfRange() {
        return new InvalidParametersException("transaction_index exceeds the number of transactions in the block");
    }
}
