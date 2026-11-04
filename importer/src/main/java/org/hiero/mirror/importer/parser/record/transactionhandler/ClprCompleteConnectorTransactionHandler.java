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
import org.hiero.mirror.common.util.SignatureUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

@Named
@RequiredArgsConstructor
class ClprCompleteConnectorTransactionHandler extends AbstractTransactionHandler {

    private final ClprConnectorCommitmentService clprConnectorCommitmentService;
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

        // ClprConnector has no commitment field (unlike Channel), so this must be recomputed, not read off state --
        // it's what identifies the PENDING row (from ClprRegisterConnector) this completion updates.
        final var commitment = SignatureUtils.keccak256(connectorId, publicKey);

        final var connector = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(clprConnectorValue.getChannelId()))
                .commitment(commitment)
                .connectorId(connectorId)
                .createdTimestamp(consensusTimestamp)
                .connectorContractId(connectorContractId)
                .adminKey(clprConnectorValue.getAdminKey().toByteArray())
                .lockedStake(clprConnectorValue.getLockedStake())
                .slashCount((long) clprConnectorValue.getSlashCount())
                .inFlightMessageCount(clprConnectorValue.getInFlightMessageCount())
                .status(ClprConnectorStatus.ACTIVE)
                .publicKey(publicKey)
                .salt(DomainUtils.toBytes(transactionBody.getSalt()))
                .signature(DomainUtils.toBytes(transactionBody.getSignature()))
                .signatureScheme(ClprEnums.toSignatureScheme(transactionBody.getSignatureScheme()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
        clprConnectorCommitmentService.notify(connector);
        recordItem.addEntityId(connectorContractId);
    }
}
