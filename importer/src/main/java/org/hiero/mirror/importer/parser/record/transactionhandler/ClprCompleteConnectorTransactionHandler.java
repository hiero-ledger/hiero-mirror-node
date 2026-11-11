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

    private final ClprConnectorService clprConnectorService;
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
        final var entry = clprContext == null ? null : clprContext.getClprConnector();
        if (ClprStateChangeHandler.isMissingStateChange(
                entry, "clpr_connector_value", "ClprCompleteConnector", recordItem.getConsensusTimestamp())) {
            return;
        }

        final var clprConnectorValue = entry.value();
        final long consensusTimestamp = recordItem.getConsensusTimestamp();
        final var connectorId = DomainUtils.toBytes(entry.key().getConnectorId());
        final var publicKey = DomainUtils.toBytes(transactionBody.getPublicKey());
        final var connectorContractId = EntityId.of(clprConnectorValue.getConnectorContract());

        // ClprConnector has no commitment field (unlike Channel), so this must be recomputed, not read off state --
        // it's what identifies the PENDING row (from ClprRegisterConnector) this completion updates.
        final var commitment = SignatureUtils.keccak256(connectorId, publicKey);

        final var connectorBuilder = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(entry.key().getChannelId()))
                .commitment(commitment)
                .connectorId(connectorId)
                .createdTimestamp(consensusTimestamp)
                .status(ClprConnectorStatus.ACTIVE)
                .publicKey(publicKey)
                .salt(DomainUtils.toBytes(transactionBody.getSalt()))
                .signature(DomainUtils.toBytes(transactionBody.getSignature()))
                .signatureScheme(ClprEnums.toSignatureScheme(transactionBody.getSignatureScheme()))
                .timestampRange(Range.atLeast(consensusTimestamp));
        ClprStateChangeHandler.applyConnectorFields(connectorBuilder, clprConnectorValue);
        final var connector = connectorBuilder.build();
        entityListener.onClprConnector(connector);
        clprConnectorService.notify(connector);
        recordItem.addEntityId(connectorContractId);
    }
}
