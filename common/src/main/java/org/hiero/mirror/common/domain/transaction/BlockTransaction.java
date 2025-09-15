// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static com.hedera.hapi.block.stream.trace.protoc.TraceData.DataCase.AUTO_ASSOCIATE_TRACE_DATA;
import static com.hedera.hapi.block.stream.trace.protoc.TraceData.DataCase.EVM_TRACE_DATA;
import static com.hedera.hapi.block.stream.trace.protoc.TraceData.DataCase.SUBMIT_MESSAGE_TRACE_DATA;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.trace.protoc.AutoAssociateTraceData;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.hapi.block.stream.trace.protoc.SubmitMessageTraceData;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.CustomLog;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.util.CollectionUtils;

@AllArgsConstructor(access = AccessLevel.NONE)
@CustomLog
@SuppressWarnings("deprecation")
@Value
public class BlockTransaction implements StreamItem {

    private static final MessageDigest DIGEST = createSha384Digest();

    private final AutoAssociateTraceData autoAssociateTraceData;
    private final long consensusTimestamp;
    private final EvmTraceData evmTraceData;
    private final BlockTransaction parent;
    private final Long parentConsensusTimestamp;
    private final BlockTransaction previous;
    private final List<StateChanges> stateChanges;
    private final SignedTransaction signedTransaction;
    private final byte[] signedTransactionBytes;

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    private final StateChangeContext stateChangeContext = createStateChangeContext();

    private final SubmitMessageTraceData submitMessageTraceData;
    private final boolean successful;
    private final List<TraceData> traceData;
    private final TransactionBody transactionBody;

    @Getter(lazy = true)
    private final ByteString transactionHash = calculateTransactionHash();

    @Getter(value = AccessLevel.NONE)
    private final Map<TransactionCase, TransactionOutput> transactionOutputs;

    private final TransactionResult transactionResult;

    @Builder(toBuilder = true)
    public BlockTransaction(
            BlockTransaction previous,
            SignedTransaction signedTransaction,
            byte[] signedTransactionBytes,
            List<StateChanges> stateChanges,
            List<TraceData> traceData,
            TransactionBody transactionBody,
            TransactionResult transactionResult,
            Map<TransactionCase, TransactionOutput> transactionOutputs) {
        this.previous = previous;
        this.signedTransaction = signedTransaction;
        this.signedTransactionBytes = signedTransactionBytes;
        this.stateChanges = stateChanges;
        this.traceData = traceData;
        this.transactionBody = transactionBody;
        this.transactionResult = transactionResult;
        this.transactionOutputs = transactionOutputs;

        consensusTimestamp = DomainUtils.timestampInNanosMax(transactionResult.getConsensusTimestamp());
        parentConsensusTimestamp = transactionResult.hasParentConsensusTimestamp()
                ? DomainUtils.timestampInNanosMax(transactionResult.getParentConsensusTimestamp())
                : null;
        parent = parseParent();
        successful = parseSuccess();

        var traceDataMap = parseTraceData();
        autoAssociateTraceData =
                getTraceDataItem(traceDataMap, AUTO_ASSOCIATE_TRACE_DATA, TraceData::getAutoAssociateTraceData);
        evmTraceData = getTraceDataItem(traceDataMap, EVM_TRACE_DATA, TraceData::getEvmTraceData);
        submitMessageTraceData =
                getTraceDataItem(traceDataMap, SUBMIT_MESSAGE_TRACE_DATA, TraceData::getSubmitMessageTraceData);
    }

    public Transaction getTransaction() {
        var builder = Transaction.newBuilder();
        if (signedTransaction.getUseSerializedTxMessageHashAlgorithm()) {
            return builder.setBodyBytes(signedTransaction.getBodyBytes())
                    .setSigMap(signedTransaction.getSigMap())
                    .build();
        }

        return builder.setSignedTransactionBytes(DomainUtils.fromBytes(signedTransactionBytes))
                .build();
    }

    public Optional<TransactionOutput> getTransactionOutput(TransactionCase transactionCase) {
        return Optional.ofNullable(transactionOutputs.get(transactionCase));
    }

    private ByteString calculateTransactionHash() {
        if (!Objects.requireNonNull(signedTransaction).getUseSerializedTxMessageHashAlgorithm()) {
            return digest(signedTransactionBytes);
        }

        // handle SignedTransaction unified by consensus nodes from a Transaction proto message with
        // Transaction.bodyBytes and Transaction.sigMap set
        var transaction = Transaction.newBuilder()
                .setBodyBytes(signedTransaction.getBodyBytes())
                .setSigMap(signedTransaction.getSigMap())
                .build();
        return digest(transaction.toByteArray());
    }

    private StateChangeContext createStateChangeContext() {
        if (parent != null) {
            return parent.getStateChangeContext();
        }

        return !CollectionUtils.isEmpty(stateChanges)
                ? new StateChangeContext(stateChanges)
                : StateChangeContext.EMPTY_CONTEXT;
    }

    private BlockTransaction parseParent() {
        if (parentConsensusTimestamp != null && previous != null) {
            if (parentConsensusTimestamp == previous.consensusTimestamp) {
                return previous;
            } else if (previous.parent != null && parentConsensusTimestamp == previous.parent.consensusTimestamp) {
                // check older siblings parent, if child count is > 1 this prevents having to search to parent
                return previous.parent;
            } else if (previous.parent != null
                    && parentConsensusTimestamp.equals(previous.parent.parentConsensusTimestamp)) {
                // batch transactions can have inner transactions with n children. The child's parent will be the inner
                // transaction so following items in batch may need to look at the grandparent
                return previous.parent.parent;
            }
        }

        return this.parent;
    }

    private boolean parseSuccess() {
        if (parent != null && !parent.successful) {
            return false;
        }

        var status = transactionResult.getStatus();
        return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                || status == ResponseCodeEnum.SUCCESS
                || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
    }

    private Map<TraceData.DataCase, TraceData> parseTraceData() {
        if (CollectionUtils.isEmpty(traceData)) {
            return Collections.emptyMap();
        }

        var result = new HashMap<TraceData.DataCase, TraceData>();
        for (var item : traceData) {
            var dataCase = item.getDataCase();
            switch (dataCase) {
                case AUTO_ASSOCIATE_TRACE_DATA:
                case EVM_TRACE_DATA:
                case SUBMIT_MESSAGE_TRACE_DATA:
                    // there should be at most one for each case
                    result.putIfAbsent(dataCase, item);
                    break;
                default:
                    log.warn("Unknown trace data case {} for transaction at {}", dataCase, consensusTimestamp);
                    break;
            }
        }

        return result;
    }

    private static ByteString digest(byte[] data) {
        return DomainUtils.fromBytes(DIGEST.digest(data));
    }

    private static <T extends MessageLite> T getTraceDataItem(
            Map<TraceData.DataCase, TraceData> data, TraceData.DataCase dataCase, Function<TraceData, T> getter) {
        var traceData = data.get(dataCase);
        return traceData != null ? getter.apply(traceData) : null;
    }
}
