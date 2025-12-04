// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static com.hedera.hapi.block.stream.trace.protoc.TraceData.DataCase.EVM_TRACE_DATA;
import static com.hedera.hapi.block.stream.trace.protoc.TraceData.DataCase.SUBMIT_MESSAGE_TRACE_DATA;
import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;
import static org.hiero.mirror.common.util.DomainUtils.normalize;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.MessageLite;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.hapi.block.stream.trace.protoc.EvmTraceData;
import com.hedera.hapi.block.stream.trace.protoc.TraceData;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SlotKey;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.CustomLog;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.common.domain.topic.TopicMessage;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.util.CollectionUtils;

@AllArgsConstructor(access = AccessLevel.NONE)
@CustomLog
@SuppressWarnings("deprecation")
@Value
public class BlockTransaction implements StreamItem {

    private static final MessageDigest DIGEST = createSha384Digest();

    private final long consensusTimestamp;

    @NonFinal
    @Setter
    private Map<SlotKey, ByteString> contractStorageReads = Collections.emptyMap();

    private final EvmTraceData evmTraceData;

    @NonFinal
    @Setter
    @ToString.Exclude
    private BlockTransaction nextInBatch;

    private final BlockTransaction parent;
    private final Long parentConsensusTimestamp;
    private final BlockTransaction previous;
    private final List<StateChanges> stateChanges;
    private final SignedTransaction signedTransaction;
    private final byte[] signedTransactionBytes;
    private final TopicID topicId; // for consensus submit message transaction

    @Getter(AccessLevel.NONE)
    private final AtomicReference<TopicMessage> topicMessage = new AtomicReference<>();

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    private final StateChangeContext stateChangeContext = createStateChangeContext();

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
            final BlockTransaction previous,
            final SignedTransaction signedTransaction,
            final byte[] signedTransactionBytes,
            final List<StateChanges> stateChanges,
            final List<TraceData> traceData,
            final TransactionBody transactionBody,
            final TransactionResult transactionResult,
            final Map<TransactionCase, TransactionOutput> transactionOutputs) {
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

        final var traceDataMap = parseTraceData();
        evmTraceData = getTraceDataItem(traceDataMap, EVM_TRACE_DATA, TraceData::getEvmTraceData);

        final var submitMessageTraceData =
                getTraceDataItem(traceDataMap, SUBMIT_MESSAGE_TRACE_DATA, TraceData::getSubmitMessageTraceData);
        if (submitMessageTraceData != null) {
            topicMessage.set(TopicMessage.builder()
                    .runningHash(DomainUtils.toBytes(submitMessageTraceData.getRunningHash()))
                    .sequenceNumber(submitMessageTraceData.getSequenceNumber())
                    .build());
        }

        topicId = transactionBody.hasConsensusSubmitMessage()
                ? transactionBody.getConsensusSubmitMessage().getTopicID()
                : null;
    }

    public TopicMessage getTopicMessage() {
        if (topicId == null) {
            return null;
        }

        if (topicMessage.get() == null) {
            topicMessage.set(getStateChangeContext().getTopicMessage(topicId).orElse(null));
        }

        return topicMessage.get();
    }

    public Transaction getTransaction() {
        final var builder = Transaction.newBuilder();
        if (signedTransaction.getUseSerializedTxMessageHashAlgorithm()) {
            return builder.setBodyBytes(signedTransaction.getBodyBytes())
                    .setSigMap(signedTransaction.getSigMap())
                    .build();
        }

        return builder.setSignedTransactionBytes(DomainUtils.fromBytes(signedTransactionBytes))
                .build();
    }

    /**
     * Get value written to the storage slot at {@link SlotKey} by the transaction. Returns the first read value in
     * subsequent transactions, or the value in statechanges.
     *
     * @param slotKey - The contract storage's slot key
     * @return The value written
     */
    public BytesValue getValueWritten(final SlotKey slotKey) {
        final var normalizedSlotKey = normalize(slotKey);
        for (var nextInner = nextInBatch; nextInner != null; nextInner = nextInner.getNextInBatch()) {
            final var valueRead = nextInner.getValueRead(normalizedSlotKey);
            if (valueRead != null) {
                return BytesValue.of(valueRead);
            }
        }

        // fall back to statechanges
        return getStateChangeContext().getContractStorageValueWritten(normalizedSlotKey);
    }

    public Optional<TransactionOutput> getTransactionOutput(final TransactionCase transactionCase) {
        return Optional.ofNullable(transactionOutputs.get(transactionCase));
    }

    private ByteString calculateTransactionHash() {
        if (!Objects.requireNonNull(signedTransaction).getUseSerializedTxMessageHashAlgorithm()) {
            return digest(signedTransactionBytes);
        }

        // handle SignedTransaction unified by consensus nodes from a Transaction proto message with
        // Transaction.bodyBytes and Transaction.sigMap set
        final var transaction = Transaction.newBuilder()
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

    private ByteString getValueRead(final SlotKey slotKey) {
        return contractStorageReads.get(slotKey);
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

        final var status = transactionResult.getStatus();
        return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                || status == ResponseCodeEnum.SUCCESS
                || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
    }

    private Map<TraceData.DataCase, TraceData> parseTraceData() {
        if (CollectionUtils.isEmpty(traceData)) {
            return Collections.emptyMap();
        }

        final var result = new HashMap<TraceData.DataCase, TraceData>();
        for (var item : traceData) {
            final var dataCase = item.getDataCase();
            switch (dataCase) {
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

    private static ByteString digest(final byte[] data) {
        return DomainUtils.fromBytes(DIGEST.digest(data));
    }

    private static <T extends MessageLite> T getTraceDataItem(
            final Map<TraceData.DataCase, TraceData> data,
            final TraceData.DataCase dataCase,
            final Function<TraceData, T> getter) {
        final var traceData = data.get(dataCase);
        return traceData != null ? getter.apply(traceData) : null;
    }
}
