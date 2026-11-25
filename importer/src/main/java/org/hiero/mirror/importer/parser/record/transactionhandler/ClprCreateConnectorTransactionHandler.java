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
import org.hiero.mirror.importer.domain.EntityIdService;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
final class ClprCreateConnectorTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;
    private final EntityListener entityListener;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        final var contractId =
                recordItem.getTransactionBody().getClprCreateConnector().getConnectorContract();
        return entityIdService.lookup(contractId, false).orElse(EntityId.EMPTY);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCREATECONNECTOR;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        // Every field the Connector row needs is on the transaction body itself -- the consensus node derives and
        // verifies connector_id from the identity key and salt, and validates channel_id and connector_contract, so
        // no state-change lookup is needed here.
        final var transactionBody = recordItem.getTransactionBody().getClprCreateConnector();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var connectorContractId = transaction.getEntityId();
        if (EntityId.isEmpty(connectorContractId)) {
            Utility.handleRecoverableError(
                    "Unable to resolve connector contract {} for ClprCreateConnector at {}",
                    transactionBody.getConnectorContract(),
                    consensusTimestamp);
            return;
        }
        final var connector = ClprConnector.builder()
                .adminKey(DomainUtils.toBytes(transactionBody.getAdminKey().toByteString()))
                .channelId(DomainUtils.toBytes(transactionBody.getChannelId()))
                .connectorContractId(connectorContractId)
                .connectorId(DomainUtils.toBytes(transactionBody.getConnectorId()))
                .createdTimestamp(consensusTimestamp)
                .lockedStake(transactionBody.getLockedStake())
                .status(ClprConnectorStatus.ACTIVE)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
    }
}
