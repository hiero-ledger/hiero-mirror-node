// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnector;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.clpr.ClprPendingCommitmentStatus;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.common.util.SignatureUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

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

        final var transactionBody = recordItem.getTransactionBody().getClprCompleteConnector();
        final var clprContext = recordItem.getClprContext();
        final var clprConnectorValue = clprContext != null ? clprContext.getClprConnector() : null;
        if (ClprStateChangeHandler.isMissingStateChange(
                clprConnectorValue,
                "clpr_connector_value",
                "ClprCompleteConnector",
                recordItem.getConsensusTimestamp())) {
            return;
        }

        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var connectorId = DomainUtils.toBytes(clprConnectorValue.getConnectorId());
        final var publicKey = DomainUtils.toBytes(transactionBody.getPublicKey());
        final var connectorContractId = EntityId.of(clprConnectorValue.getConnectorContract());

        final var connector = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(clprConnectorValue.getChannelId()))
                .connectorId(connectorId)
                .connectorContractId(connectorContractId)
                .adminKey(clprConnectorValue.getAdminKey().toByteArray())
                .lockedStake(clprConnectorValue.getLockedStake())
                .slashCount((long) clprConnectorValue.getSlashCount())
                .inFlightMessageCount(clprConnectorValue.getInFlightMessageCount())
                .createdTimestamp(consensusTimestamp)
                .deleted(false)
                .publicKey(publicKey)
                .salt(DomainUtils.toBytes(transactionBody.getSalt()))
                .signature(DomainUtils.toBytes(transactionBody.getSignature()))
                .signatureScheme(ClprEnums.toSignatureScheme(transactionBody.getSignatureScheme()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
        recordItem.addEntityId(connectorContractId);

        // ClprConnector has no commitment field (unlike Channel), so this must be recomputed, not read off state.
        final var commitment = ClprConnectorPendingCommitment.builder()
                .commitment(SignatureUtils.keccak256(connectorId, publicKey))
                .status(ClprPendingCommitmentStatus.COMPLETED)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnectorPendingCommitment(commitment);
    }
}
