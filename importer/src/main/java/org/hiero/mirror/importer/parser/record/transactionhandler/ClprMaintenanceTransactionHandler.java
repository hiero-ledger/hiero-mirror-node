// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.clpr.ClprMaintenance;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;

/**
 * {@code ClprMaintenance}'s singleton updates (endpoint manifest, ledger configuration) only exist in their own
 * state changes, which {@link ClprStateChangeHandler#process} already handles generically. This only persists the
 * transaction's own {@code reason} field, which has no corresponding state change.
 */
@Named
@RequiredArgsConstructor
class ClprMaintenanceTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRMAINTENANCE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        entityListener.onClprMaintenance(ClprMaintenance.builder()
                .consensusTimestamp(recordItem.getConsensusTimestamp())
                .reason(recordItem.getTransactionBody().getClprMaintenance().getReasonValue())
                .build());
    }
}
