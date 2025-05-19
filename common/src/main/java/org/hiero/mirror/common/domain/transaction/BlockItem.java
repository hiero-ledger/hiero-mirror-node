// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.common.exception.ProtobufException;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.util.CollectionUtils;

@AllArgsConstructor(access = AccessLevel.NONE)
@Value
public class BlockItem implements StreamItem {

    private final long consensusTimestamp;
    private final BlockItem parent;
    private final Long parentConsensusTimestamp;
    private final BlockItem previous;
    private final List<StateChanges> stateChanges;
    private final boolean successful;
    private final Transaction transaction;
    private final TransactionBody transactionBody;
    private final SignatureMap signatureMap;

    @Getter(value = AccessLevel.NONE)
    private final Map<TransactionCase, TransactionOutput> transactionOutputs;

    private final TransactionResult transactionResult;

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    private final StateChangeContext stateChangeContext = createStateChangeContext();

    @Builder(toBuilder = true)
    public BlockItem(
            Transaction transaction,
            TransactionResult transactionResult,
            Map<TransactionCase, TransactionOutput> transactionOutputs,
            List<StateChanges> stateChanges,
            BlockItem previous,
            TransactionBody transactionBody,
            SignatureMap signatureMap) {
        this.transaction = transaction;
        this.transactionResult = transactionResult;
        this.transactionOutputs = transactionOutputs;
        this.stateChanges = stateChanges;
        this.previous = previous;

        consensusTimestamp = DomainUtils.timestampInNanosMax(transactionResult.getConsensusTimestamp());
        parentConsensusTimestamp = transactionResult.hasParentConsensusTimestamp()
                ? DomainUtils.timestampInNanosMax(transactionResult.getParentConsensusTimestamp())
                : null;
        parent = parseParent();
        successful = parseSuccess();
        this.transactionBody = transactionBody;
        this.signatureMap = signatureMap;
    }

    public Optional<TransactionOutput> getTransactionOutput(TransactionCase transactionCase) {
        return Optional.ofNullable(transactionOutputs.get(transactionCase));
    }

    private StateChangeContext createStateChangeContext() {
        if (parent != null) {
            return parent.getStateChangeContext();
        }

        return !CollectionUtils.isEmpty(stateChanges)
                ? new StateChangeContext(stateChanges)
                : StateChangeContext.EMPTY_CONTEXT;
    }

    private BlockItem parseParent() {
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

    public static class BlockItemBuilder {
        public BlockItem build() {
            parseBody();
            return new BlockItem(
                    transaction,
                    transactionResult,
                    transactionOutputs,
                    stateChanges,
                    previous,
                    transactionBody,
                    signatureMap);
        }

        @SuppressWarnings("deprecation")
        private void parseBody() {
            if (transactionBody == null || signatureMap == null) {
                try {
                    if (transaction.getSignedTransactionBytes().isEmpty()) {
                        this.transactionBody(TransactionBody.parseFrom(transaction.getBodyBytes()))
                                .signatureMap(transaction.getSigMap());
                    } else {
                        var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
                        this.transactionBody(TransactionBody.parseFrom(signedTransaction.getBodyBytes()))
                                .signatureMap(signedTransaction.getSigMap());
                    }
                } catch (InvalidProtocolBufferException e) {
                    throw new ProtobufException("Error parsing transaction body from transaction", e);
                }
            }
        }
    }
}
