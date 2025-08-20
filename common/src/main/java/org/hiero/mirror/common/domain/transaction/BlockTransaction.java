// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.common.domain.transaction;

import static org.hiero.mirror.common.util.DomainUtils.createSha384Digest;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput.TransactionCase;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;
import org.hiero.mirror.common.domain.StreamItem;
import org.hiero.mirror.common.util.DomainUtils;
import org.springframework.util.CollectionUtils;

@AllArgsConstructor(access = AccessLevel.NONE)
@Value
public class BlockTransaction implements StreamItem {

    private static final MessageDigest DIGEST = createSha384Digest();

    private final long consensusTimestamp;
    private final BlockTransaction parent;
    private final Long parentConsensusTimestamp;
    private final BlockTransaction previous;
    private final List<StateChanges> stateChanges;
    private final SignedTransaction signedTransaction;
    private final byte[] signedTransactionBytes;
    private final boolean successful;
    private final TransactionBody transactionBody;

    @Getter(lazy = true)
    private final ByteString transactionHash = calculateTransactionHash();

    @Getter(value = AccessLevel.NONE)
    private final Map<TransactionCase, TransactionOutput> transactionOutputs;

    private final TransactionResult transactionResult;

    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    private final StateChangeContext stateChangeContext = createStateChangeContext();

    @Builder(toBuilder = true)
    public BlockTransaction(
            BlockTransaction previous,
            SignedTransaction signedTransaction,
            byte[] signedTransactionBytes,
            List<StateChanges> stateChanges,
            TransactionBody transactionBody,
            TransactionResult transactionResult,
            Map<TransactionCase, TransactionOutput> transactionOutputs) {
        this.previous = previous;
        this.signedTransaction = signedTransaction;
        this.signedTransactionBytes = signedTransactionBytes;
        this.stateChanges = stateChanges;
        this.transactionBody = transactionBody;
        this.transactionResult = transactionResult;
        this.transactionOutputs = transactionOutputs;

        consensusTimestamp = DomainUtils.timestampInNanosMax(transactionResult.getConsensusTimestamp());
        parentConsensusTimestamp = transactionResult.hasParentConsensusTimestamp()
                ? DomainUtils.timestampInNanosMax(transactionResult.getParentConsensusTimestamp())
                : null;
        parent = parseParent();
        successful = parseSuccess();
    }

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
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

    private static ByteString digest(byte[] data) {
        return DomainUtils.fromBytes(DIGEST.digest(data));
    }
}
