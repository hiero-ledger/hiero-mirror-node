// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
class ClprRegisterConnectorTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRREGISTERCONNECTOR;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        // channel_id is on the transaction body itself -- the consensus node validates it references an existing
        // Channel and rejects the transaction otherwise, so no state-change lookup is needed here.
        final var transactionBody = recordItem.getTransactionBody().getClprRegisterConnector();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var connector = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(transactionBody.getChannelId()))
                .commitment(DomainUtils.toBytes(transactionBody.getCommitment()))
                .createdTimestamp(consensusTimestamp)
                .status(ClprConnectorStatus.PENDING)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
    }
}
