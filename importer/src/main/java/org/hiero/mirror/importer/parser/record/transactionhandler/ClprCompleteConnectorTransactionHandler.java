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
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var connectorId = DomainUtils.toBytes(transactionBody.getConnectorId());
        var publicKey = DomainUtils.toBytes(transactionBody.getPublicKey());
        var connectorContractId = EntityId.of(transactionBody.getConnectorContract());

        var connector = ClprConnector.builder()
                .channelId(DomainUtils.toBytes(transactionBody.getChannelId()))
                .connectorId(connectorId)
                .connectorContractId(connectorContractId)
                .adminKey(transactionBody.getAdminKey().toByteArray())
                .lockedStake(transactionBody.getLockedStake())
                .createdTimestamp(consensusTimestamp)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnector(connector);
        recordItem.addEntityId(connectorContractId);

        // Recomputes the keccak256(connectorId || publicKey) commitment from this transaction's own fields; no
        // verifier/state-change data needed
        var commitment = ClprConnectorPendingCommitment.builder()
                .commitment(SignatureUtils.keccak256(connectorId, publicKey))
                .completedTimestamp(consensusTimestamp)
                .publicKey(publicKey)
                .salt(DomainUtils.toBytes(transactionBody.getSalt()))
                .signature(DomainUtils.toBytes(transactionBody.getSignature()))
                .signatureScheme(ClprEnums.toSignatureScheme(transactionBody.getSignatureScheme()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnectorPendingCommitment(commitment);
    }
}
