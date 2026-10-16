// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
class ClprDeregisterConnectorTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRDEREGISTERCONNECTOR;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getClprDeregisterConnector();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var stakeRecipientId = EntityId.of(transactionBody.getStakeRecipient());

        // Mirror-node-only soft-delete; consensus removes the Connector from its map entirely
        var connector = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(transactionBody.getChannelId()))
                .connectorId(DomainUtils.toBytes(transactionBody.getConnectorId()))
                .deleted(true)
                .stakeRecipientId(stakeRecipientId)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
        recordItem.addEntityId(stakeRecipientId);
    }
}
