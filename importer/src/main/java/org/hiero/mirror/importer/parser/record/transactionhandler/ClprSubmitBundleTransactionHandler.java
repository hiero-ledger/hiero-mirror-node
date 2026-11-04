// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;

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

        final var clprContext = recordItem.getClprContext();
        final var clprChannelValue = clprContext != null ? clprContext.getClprChannel() : null;
        if (ClprStateChangeHandler.isMissingStateChange(
                clprChannelValue, "clpr_channel_value", "ClprSubmitBundle", recordItem.getConsensusTimestamp())) {
            return;
        }

        clprStateChangeHandler.process(recordItem);
    }
}
