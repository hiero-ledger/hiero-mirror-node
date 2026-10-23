// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprConnectorPendingCommitment;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.hiero.mirror.importer.util.Utility;

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

        var commitment = recordItem.getClprConnectorPendingCommitment();
        if (commitment == null) {
            Utility.handleRecoverableError(
                    "No clpr_pending_connector_commitments state change found for successful"
                            + " ClprRegisterConnector at {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var pendingCommitment = ClprConnectorPendingCommitment.builder()
                .commitment(DomainUtils.toBytes(commitment))
                .createdTimestamp(consensusTimestamp)
                .deleted(false)
                .timestampRange(Range.atLeast(consensusTimestamp))
                .build();
        entityListener.onClprConnectorPendingCommitment(pendingCommitment);
    }
}
