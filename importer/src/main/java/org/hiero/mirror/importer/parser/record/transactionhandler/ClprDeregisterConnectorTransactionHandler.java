// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorStatus;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
final class ClprDeregisterConnectorTransactionHandler extends AbstractTransactionHandler {

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

        final var transactionBody = recordItem.getTransactionBody().getClprDeregisterConnector();
        final var stakeRecipientId = EntityId.of(transactionBody.getStakeRecipient());
        recordItem.addEntityId(stakeRecipientId);

        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var connector = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(transactionBody.getChannelId()))
                .connectorId(DomainUtils.toBytes(transactionBody.getConnectorId()))
                .status(ClprConnectorStatus.DELETED)
                .stakeRecipientId(stakeRecipientId)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
    }
}
