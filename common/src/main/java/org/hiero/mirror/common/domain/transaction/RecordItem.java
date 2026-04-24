// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static lombok.AccessLevel.PRIVATE;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
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
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.common.domain.contract.ContractTransaction;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityTransaction;
import org.hiero.mirror.common.domain.hook.AbstractHook;
import org.hiero.mirror.common.exception.ProtobufException;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.LogsBloomFilter;
import org.springframework.data.util.Version;

@Builder(buildMethodName = "buildInternal")
@AllArgsConstructor(access = PRIVATE)
@CustomLog
@ToString(onlyExplicitlyIncluded = true)
@Value
public class RecordItem implements StreamItem {

    public static final int HOOK_CONTRACT_NUM = 365;

    static final String BAD_TRANSACTION_BYTES_MESSAGE = "Failed to parse transaction bytes";
    static final String BAD_RECORD_BYTES_MESSAGE = "Failed to parse record bytes";
    static final String BAD_TRANSACTION_BODY_BYTES_MESSAGE = "Error parsing transactionBody from transaction";

    private static final Predicate<EntityId> REJECT_ALL = _ -> false;

    // Final fields
    @Builder.Default
    private final Version hapiVersion = RecordFile.HAPI_VERSION_NOT_SET;

    @ToString.Include
    private final long consensusTimestamp;

    private final boolean blockstream;
    private final Long congestionPricingMultiplier;
    private final RecordItem hookParent;
    private final RecordItem parent;
    private final EntityId payerAccountId;
    private final RecordItem previous;
    private final SignatureMap signatureMap;
    private final boolean successful;
    private final Transaction transaction;
    private final TransactionBody transactionBody;
    private final int transactionIndex;
    private final TransactionRecord transactionRecord;
    private final int transactionType;

    @Setter
    @EqualsAndHashCode.Exclude
    @NonFinal
    private AtomicInteger logIndex;

    @NonFinal
    @Setter
    private Predicate<EntityId> contractTransactionPredicate;

    @NonFinal
    private Map<Long, ContractTransaction> contractTransactions;

    @Builder.Default
    @NonFinal
    private Predicate<EntityId> entityNftTransactionPredicate = REJECT_ALL;

    @Getter(AccessLevel.NONE)
    @NonFinal
    private EntityTransaction.EntityTransactionBuilder entityTransactionBuilder;

    @Builder.Default
    @NonFinal
    private Predicate<EntityId> entityTransactionPredicate = REJECT_ALL;

    @NonFinal
    private Map<Long, EntityTransaction> entityTransactions;

    @NonFinal
    @Setter
    private EthereumTransaction ethereumTransaction;

    @Builder.Default
    @NonFinal
    @Setter
    private List<TransactionSidecarRecord> sidecarRecords = Collections.emptyList();

    // Transient hook execution queue for CryptoTransfer transactions that may trigger hooks
    @NonFinal
    @Setter
    private ArrayDeque<AbstractHook.Id> hookExecutionQueue;

    /**
     * Bitwise OR of all synthetic contract log blooms for this record item (Ethereum receipt semantics).
     */
    @NonFinal
    private LogsBloomFilter syntheticContractLogsBloom;

    /**
     * First initialize bloom with existing bloom from ContractResult, then merges a synthetic contract log bloom into
     * the aggregate for this record item (bitwise OR).
     *
     * @param bloom bloom bytes; must be {@link LogsBloomFilter#BYTE_SIZE} bytes to be merged
     */
    public void mergeSyntheticContractLogBloom(byte[] bloom) {
        if (bloom == null || bloom.length != LogsBloomFilter.BYTE_SIZE) {
            return;
        }

        if (syntheticContractLogsBloom == null) {
            syntheticContractLogsBloom = new LogsBloomFilter();
            if (transactionRecord.hasContractCreateResult()
                    && !transactionRecord.getContractCreateResult().getBloom().isEmpty()) {
                syntheticContractLogsBloom.or(
                        transactionRecord.getContractCreateResult().getBloom().toByteArray());
            } else if (transactionRecord.hasContractCallResult()
                    && !transactionRecord.getContractCallResult().getBloom().isEmpty()) {
                syntheticContractLogsBloom.or(
                        transactionRecord.getContractCallResult().getBloom().toByteArray());
            }
        }

        syntheticContractLogsBloom.or(bloom);
    }

    /**
     * @return the merged synthetic contract log blooms, or {@code null} if none were recorded
     */
    public byte[] getMergedSyntheticContractLogsBloom() {
        return syntheticContractLogsBloom != null
                ? syntheticContractLogsBloom.toArrayUnsafe()
                : ArrayUtils.EMPTY_BYTE_ARRAY;
    }

    /**
     * Gets the next hook context from the execution queue. Returns null if no more contexts are available.
     *
     * @return the next hook execution context, or null if queue is empty
     */
    public AbstractHook.Id nextHookContext() {
        if (hookExecutionQueue == null) {
            return parent != null ? parent.nextHookContext() : null;
        }
        return hookExecutionQueue.poll();
    }

    public void addContractTransaction(EntityId entityId) {
        if (contractTransactionPredicate == null || !contractTransactionPredicate.test(entityId)) {
            return;
        }
        getContractTransactions().computeIfAbsent(entityId.getId(), key -> ContractTransaction.builder()
                .entityId(key)
                .payerAccountId(payerAccountId.getId())
                .consensusTimestamp(consensusTimestamp)
                .build());
    }

    public void addEntityId(final EntityId entityId) {
        doAddEntityId(entityId, entityTransactionPredicate);
    }

    public void addNftTransactionEntityId(final EntityId entityId) {
        doAddEntityId(entityId, entityNftTransactionPredicate);
    }

    public void setEntityNftTransactionPredicate(final Predicate<EntityId> predicate) {
        entityNftTransactionPredicate =
                predicate != null ? predicate.and(entityId -> !payerAccountId.equals(entityId)) : REJECT_ALL;
    }

    public void setEntityTransactionPredicate(final Predicate<EntityId> predicate) {
        entityTransactionPredicate = predicate != null ? predicate : REJECT_ALL;
    }

    private void doAddEntityId(final EntityId entityId, final Predicate<EntityId> predicate) {
        if (!predicate.test(entityId)) {
            return;
        }

        if (entityTransactionBuilder == null) {
            entityTransactionBuilder = EntityTransaction.builder()
                    .consensusTimestamp(consensusTimestamp)
                    .payerAccountId(payerAccountId)
                    .result(getTransactionStatus())
                    .type(transactionType);
        }

        getEntityTransactions().computeIfAbsent(entityId.getId(), id -> entityTransactionBuilder
                .entityId(id)
                .build());
    }

    public Map<Long, EntityTransaction> getEntityTransactions() {
        if (entityTransactions == null) {
            entityTransactions = new HashMap<>();
        }
        return entityTransactions;
    }

    public int getAndIncrementLogIndex() {
        if (logIndex == null) {
            logIndex = new AtomicInteger(0);
        }
        return logIndex.getAndIncrement();
    }

    public int getTransactionStatus() {
        return transactionRecord.getReceipt().getStatusValue();
    }

    public boolean isChild() {
        return transactionRecord.hasParentConsensusTimestamp();
    }

    public boolean isInvalidIdError() {
        return switch (transactionRecord.getReceipt().getStatus()) {
            case INVALID_ACCOUNT_ID,
                    INVALID_ALIAS_KEY,
                    INVALID_CONTRACT_ID,
                    INVALID_FILE_ID,
                    INVALID_NODE_ACCOUNT_ID,
                    INVALID_SCHEDULE_ID,
                    INVALID_TOKEN_ID,
                    INVALID_TOPIC_ID -> true;
            default -> false;
        };
    }

    // Whether this is a top level, user submitted transaction that could possibly trigger other internal transactions.
    public boolean isTopLevel() {
        var transactionNonce = transactionRecord.getTransactionID().getNonce();

        return transactionNonce == 0
                || (transactionNonce > 0 && transactionRecord.getTransactionID().getScheduled())
                || !transactionRecord.hasParentConsensusTimestamp()
                || isSystemFileUpdate();
    }

    // Whether we have a FileUpdate transaction that is paid by the system account 0.0.50
    private boolean isSystemFileUpdate() {
        return transactionBody.hasFileUpdate()
                && EntityId.of(
                                CommonProperties.getInstance().getShard(),
                                CommonProperties.getInstance().getRealm(),
                                50L)
                        .equals(payerAccountId);
    }

    /**
     * Check whether ethereum transaction exist in the record item and returns it hash, if not return 32-byte
     * representation of the transaction hash
     *
     * @return 32-byte transaction hash of this record item
     */
    public byte[] getTransactionHash() {
        return Optional.ofNullable(getEthereumTransaction())
                .map(EthereumTransaction::getHash)
                .orElseGet(() -> Arrays.copyOfRange(
                        DomainUtils.toBytes(getTransactionRecord().getTransactionHash()), 0, 32));
    }

    private Map<Long, ContractTransaction> getContractTransactions() {
        if (contractTransactions == null) {
            contractTransactions = new HashMap<>();
        }

        return contractTransactions;
    }

    public Collection<ContractTransaction> populateContractTransactions() {
        if (contractTransactions == null || contractTransactions.isEmpty()) {
            return Collections.emptyList();
        }
        var ids = new ArrayList<>(contractTransactions.keySet());
        contractTransactions.values().forEach(contractTransaction -> contractTransaction.setContractIds(ids));
        return contractTransactions.values();
    }

    public RecordItem getContractRelatedParent() {
        if (hookParent != null) {
            return hookParent;
        }
        if (parent != null && parent.hasContractResult()) {
            return parent;
        }
        return null;
    }

    private boolean hasContractResult() {
        return transactionRecord.hasContractCreateResult() || transactionRecord.hasContractCallResult();
    }

    private ContractFunctionResult getContractResult() {
        if (transactionRecord.hasContractCallResult()) {
            return transactionRecord.getContractCallResult();
        } else if (transactionRecord.hasContractCreateResult()) {
            return transactionRecord.getContractCreateResult();
        }

        return null;
    }

    public static class RecordItemBuilder {

        private TransactionRecord.Builder transactionRecordBuilder;

        public RecordItem build() {
            if (transactionRecordBuilder != null) {
                transactionRecord = transactionRecordBuilder.build();
            }

            parseTransaction();
            this.consensusTimestamp = DomainUtils.timestampInNanosMax(transactionRecord.getConsensusTimestamp());
            this.parent = parseParent();
            this.hookParent = parsePossiblyHookContractRelatedParent();
            this.payerAccountId = EntityId.of(transactionBody.getTransactionID().getAccountID());
            this.successful = parseSuccess();
            this.transactionType = parseTransactionType(transactionBody);
            return buildInternal();
        }

        public RecordItemBuilder transactionRecord(TransactionRecord transactionRecord) {
            this.transactionRecord = transactionRecord;
            transactionRecordBuilder = null;
            return this;
        }

        public TransactionRecord.Builder transactionRecordBuilder() {
            if (transactionRecordBuilder == null) {
                transactionRecordBuilder =
                        transactionRecord != null ? transactionRecord.toBuilder() : TransactionRecord.newBuilder();
                transactionRecord = null;
            }

            return transactionRecordBuilder;
        }

        private RecordItem parseParent() {
            if (transactionRecord.hasParentConsensusTimestamp() && previous != null) {
                var parentTimestamp = transactionRecord.getParentConsensusTimestamp();
                if (parentTimestamp.equals(previous.transactionRecord.getConsensusTimestamp())) {
                    return previous;
                } else if (previous.parent != null
                        && parentTimestamp.equals(previous.parent.transactionRecord.getConsensusTimestamp())) {
                    // check older siblings parent, if child count is > 1 this prevents having to search to parent
                    return previous.parent;
                } else if (previous.parent != null
                        && parentTimestamp.equals(previous.parent.transactionRecord.getParentConsensusTimestamp())) {
                    // batch transactions can have inner transactions with n children. The child's parent will be the
                    // inner
                    // transaction so following items in batch may need to look at the grandparent
                    return previous.parent.parent;
                }
            }
            return this.parent;
        }

        private RecordItem parsePossiblyHookContractRelatedParent() {
            final var currentTxnRecordContractResult = getContractResult();

            if (transactionRecord.hasParentConsensusTimestamp()
                    && currentTxnRecordContractResult != null
                    && currentTxnRecordContractResult.getContractID().getContractNum() != HOOK_CONTRACT_NUM
                    && previous != null) {
                var candidateRecord = previous;

                while (candidateRecord != null && candidateRecord != parent) {
                    var candidateTxnRecordContractResult = candidateRecord.getContractResult();
                    if (candidateTxnRecordContractResult == null) {
                        break;
                    }

                    if (candidateTxnRecordContractResult.getContractID().getContractNum() == HOOK_CONTRACT_NUM) {
                        // we found the first hook child item, which has a ContractResult and is executed via a hook
                        return candidateRecord;
                    }

                    candidateRecord = candidateRecord.previous;
                }
            }

            return null;
        }

        private ContractFunctionResult getContractResult() {
            if (transactionRecord.hasContractCallResult()) {
                return transactionRecord.getContractCallResult();
            } else if (transactionRecord.hasContractCreateResult()) {
                return transactionRecord.getContractCreateResult();
            }

            return null;
        }

        private boolean parseSuccess() {
            // A child is only successful if its parent is as well. Consensus nodes have had issues in the past where
            // that
            // invariant did not hold true and children contract calls were not reverted on failure.
            if (parent != null && !parent.isSuccessful()) {
                return false;
            }

            var status = transactionRecord.getReceipt().getStatus();
            return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                    || status == ResponseCodeEnum.SUCCESS
                    || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
        }

        /**
         * Parses the transaction into separate TransactionBody and SignatureMap objects. Necessary since the
         * Transaction payload has changed incompatibly multiple times over its lifetime.
         * <p>
         * Not possible to check the existence of bodyBytes or signedTransactionBytes fields since there are no
         * 'hasBodyBytes()' or 'hasSignedTransactionBytes()` methods. If unset, they return empty ByteString which
         * always parses successfully to an empty TransactionBody. However, every transaction should have a valid
         * (non-empty) TransactionBody.
         */
        @SuppressWarnings("deprecation")
        private void parseTransaction() {
            if (transactionBody == null || signatureMap == null) {
                try {
                    if (!transaction.getSignedTransactionBytes().equals(ByteString.EMPTY)) {
                        var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
                        this.transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
                        this.signatureMap = signedTransaction.getSigMap();
                    } else if (!transaction.getBodyBytes().equals(ByteString.EMPTY)) {
                        this.transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
                        this.signatureMap = transaction.getSigMap();
                    } else if (transaction.hasBody()) {
                        this.transactionBody = transaction.getBody();
                        this.signatureMap = transaction.getSigMap();
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE, e);
                }
            }

            if (transactionBody == null || signatureMap == null) {
                throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE);
            }
        }

        /**
         * Because body.getDataCase() can return null for unknown transaction types, we instead get oneof generically
         *
         * @return The protobuf ID that represents the transaction type
         */
        private int parseTransactionType(TransactionBody body) {
            TransactionBody.DataCase dataCase = body.getDataCase();

            if (dataCase == null || dataCase == TransactionBody.DataCase.DATA_NOT_SET) {
                Set<Integer> unknownFields = body.getUnknownFields().asMap().keySet();

                if (unknownFields.size() != 1) {
                    log.error(
                            "Unable to guess correct transaction type since there's not exactly one unknown field {}: {}",
                            unknownFields,
                            Hex.encodeHexString(body.toByteArray()));
                    return TransactionBody.DataCase.DATA_NOT_SET.getNumber();
                }

                int genericTransactionType = unknownFields.iterator().next();
                log.warn("Encountered unknown transaction type: {}", genericTransactionType);
                return genericTransactionType;
            }

            return dataCase.getNumber();
        }
    }
}
