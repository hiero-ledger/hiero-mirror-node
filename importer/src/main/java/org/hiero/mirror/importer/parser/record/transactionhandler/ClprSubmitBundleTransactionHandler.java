// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor
class ClprSubmitBundleTransactionHandler extends AbstractTransactionHandler {

    private final ClprStateChangeHandler clprStateChangeHandler;

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRSUBMITBUNDLE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        if (recordItem.getClprChannel() == null) {
            Utility.handleRecoverableError(
                    "No clpr_channel_value state change found for successful ClprSubmitBundle at {}",
                    recordItem.getConsensusTimestamp());
            return;
        }

        clprStateChangeHandler.process(recordItem);
    }
}
