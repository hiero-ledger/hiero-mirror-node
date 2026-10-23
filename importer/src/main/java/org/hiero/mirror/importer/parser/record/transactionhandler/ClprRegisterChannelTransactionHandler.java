// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

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

        var ownershipCommitment = recordItem.getClprChannelPendingCommitment();
        if (ownershipCommitment == null) {
            Utility.handleRecoverableError(
                    "No clpr_pending_commitments state change found for successful ClprRegisterChannel at {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();

        // deleted must be set explicitly: a swept commitment can be legitimately re-registered, and leaving it
        // unset would let the upsert coalesce preserve the prior sweep's deleted = true
        var commitment = ClprChannelPendingCommitment.builder()
                .createdTimestamp(consensusTimestamp)
                .deleted(false)
                .ownershipCommitment(DomainUtils.toBytes(ownershipCommitment))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprChannelPendingCommitment(commitment);
    }
}
