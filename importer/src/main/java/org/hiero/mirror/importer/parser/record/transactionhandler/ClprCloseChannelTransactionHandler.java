// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@CustomLog
@Named
@RequiredArgsConstructor
class ClprCloseChannelTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCLOSECHANNEL;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var clprContext = recordItem.getClprContext();
        final var clprChannelValue = clprContext == null ? null : clprContext.getClprChannel();
        if (clprChannelValue == null) {
            final var transactionBody = recordItem.getTransactionBody().getClprCloseChannel();
            log.info("Sweeping abandoned ClprChannelPendingCommitment at {}", consensusTimestamp);
            final var commitment = ClprChannelPendingCommitment.builder()
                    .ownershipCommitment(DomainUtils.toBytes(transactionBody.getOwnershipCommitment()))
                    .status(ClprPendingCommitmentStatus.DELETED)
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .build();
            entityListener.onClprChannelPendingCommitment(commitment);
            return;
        }

        final var builder = ClprStateChangeHandler.populateFromStateChange(clprChannelValue);
        entityListener.onClprChannel(
                builder.timestampRange(Range.atLeast(consensusTimestamp)).build());
    }
}
