// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprChannel;
import org.hiero.mirror.common.domain.clpr.ClprChannelPendingCommitment;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

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

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var clprChannelValue = recordItem.getClprChannel();
        if (clprChannelValue == null) {
            // No Channel exists yet for this channel_id -- an admin sweep of an abandoned commitment, not a real
            // closure; the only branch that ever marks a commitment deleted
            var transactionBody = recordItem.getTransactionBody().getClprCloseChannel();
            var commitment = ClprChannelPendingCommitment.builder()
                    .ownershipCommitment(DomainUtils.toBytes(transactionBody.getOwnershipCommitment()))
                    .deleted(true)
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .build();
            entityListener.onClprChannelPendingCommitment(commitment);
            return;
        }

        var builder = ClprChannel.builder();
        ClprStateChangeHandler.populateFromStateChange(builder, clprChannelValue);
        entityListener.onClprChannel(
                builder.timestampRange(Range.atLeast(consensusTimestamp)).build());
    }
}
