// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class ClprCompleteConnectorTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCOMPLETECONNECTOR;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getClprCompleteConnector();
        var clprConnectorValue = recordItem.getClprConnector();
        if (clprConnectorValue == null) {
            Utility.handleRecoverableError(
                    "No clpr_connector_value state change found for successful ClprCompleteConnector at {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var connectorId = DomainUtils.toBytes(clprConnectorValue.getConnectorId());
        var publicKey = DomainUtils.toBytes(transactionBody.getPublicKey());
        var connectorContractId = EntityId.of(clprConnectorValue.getConnectorContract());

        var connector = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(clprConnectorValue.getChannelId()))
                .connectorId(connectorId)
                .connectorContractId(connectorContractId)
                .adminKey(clprConnectorValue.getAdminKey().toByteArray())
                .lockedStake(clprConnectorValue.getLockedStake())
                .slashCount((long) clprConnectorValue.getSlashCount())
                .inFlightMessageCount(clprConnectorValue.getInFlightMessageCount())
                .createdTimestamp(consensusTimestamp)
                .publicKey(publicKey)
                .salt(DomainUtils.toBytes(transactionBody.getSalt()))
                .signature(DomainUtils.toBytes(transactionBody.getSignature()))
                .signatureScheme(ClprEnums.toSignatureScheme(transactionBody.getSignatureScheme()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
        recordItem.addEntityId(connectorContractId);

        // ClprConnector has no commitment field (unlike Channel), so this must be recomputed, not read off state.
        var commitment = ClprConnectorPendingCommitment.builder()
                .commitment(SignatureUtils.keccak256(connectorId, publicKey))
                .completedTimestamp(consensusTimestamp)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnectorPendingCommitment(commitment);
    }
}
