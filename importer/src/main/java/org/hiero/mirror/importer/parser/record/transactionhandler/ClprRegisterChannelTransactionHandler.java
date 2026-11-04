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

        final var clprContext = recordItem.getClprContext();
        final var ownershipCommitment = clprContext != null ? clprContext.getClprChannelPendingCommitment() : null;
        if (ClprStateChangeHandler.isMissingStateChange(
                ownershipCommitment,
                "clpr_pending_commitments",
                "ClprRegisterChannel",
                recordItem.getConsensusTimestamp())) {
            return;
        }

        final long consensusTimestamp = recordItem.getConsensusTimestamp();

        // status must be set explicitly: a swept commitment can be legitimately re-registered, and leaving it
        // unset would let the upsert coalesce preserve the prior sweep's DELETED status
        final var commitment = ClprChannelPendingCommitment.builder()
                .createdTimestamp(consensusTimestamp)
                .status(ClprPendingCommitmentStatus.OPEN)
                .ownershipCommitment(DomainUtils.toBytes(ownershipCommitment))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannelPendingCommitment(commitment);
    }
}
