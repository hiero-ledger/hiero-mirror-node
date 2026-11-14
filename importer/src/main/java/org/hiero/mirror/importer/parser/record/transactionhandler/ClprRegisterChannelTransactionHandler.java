// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
class ClprRegisterChannelTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRREGISTERCHANNEL;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        // ownership_commitment is already on the transaction body itself, so there's no need to round-trip it
        // through the per-RecordItem ClprContext.
        final var transactionBody = recordItem.getTransactionBody().getClprRegisterChannel();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();

        // status must be set explicitly: a swept commitment can be legitimately re-registered, and leaving it
        // unset would let the upsert coalesce preserve the prior sweep's DELETED status
        final var commitment = ClprChannelPendingCommitment.builder()
                .createdTimestamp(consensusTimestamp)
                .status(ClprPendingCommitmentStatus.OPEN)
                .ownershipCommitment(DomainUtils.toBytes(transactionBody.getOwnershipCommitment()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannelPendingCommitment(commitment);
    }
}
