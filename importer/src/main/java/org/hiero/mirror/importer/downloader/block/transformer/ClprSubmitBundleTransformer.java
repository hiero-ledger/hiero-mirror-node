// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the dispatched {@code clpr_message_value}/{@code clpr_message_key} entries and the updated
 * {@code clpr_channel_value} (queue positions, running hashes, status) off {@code StateChangeContext} and populates
 * {@link org.hiero.mirror.common.domain.transaction.RecordItem}'s new CLPR fields for the corresponding
 * {@code TransactionHandler} to expand into {@code clpr_message} rows and a {@code clpr_channel} update.
 */
@Named
final class ClprSubmitBundleTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var transactionBody =
                blockTransactionTransformation.getTransactionBody().getClprSubmitBundle();
        var stateChangeContext = blockTransaction.getStateChangeContext();
        var recordItemBuilder = blockTransactionTransformation.recordItemBuilder();

        stateChangeContext.getClprChannel(transactionBody.getChannelId()).ifPresent(recordItemBuilder::clprChannel);
        recordItemBuilder.clprMessages(stateChangeContext.getClprMessages());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRSUBMITBUNDLE;
    }
}
