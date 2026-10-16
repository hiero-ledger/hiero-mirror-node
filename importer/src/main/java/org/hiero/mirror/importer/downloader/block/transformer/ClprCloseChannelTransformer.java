// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the resulting {@code clpr_channel_value} state change (the {@code CLOSING}/{@code DRAINED}/{@code CLOSED}
 * status transition and queue state) off {@code StateChangeContext}. Absent when no Channel existed yet for the
 * supplied {@code channel_id} -- the corresponding {@code TransactionHandler} treats that as an admin sweep of an
 * abandoned, still-pending commitment instead of a real Channel closure.
 */
@Named
final class ClprCloseChannelTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var transactionBody =
                blockTransactionTransformation.getTransactionBody().getClprCloseChannel();
        blockTransaction
                .getStateChangeContext()
                .getClprChannel(transactionBody.getChannelId())
                .ifPresent(blockTransactionTransformation.recordItemBuilder()::clprChannel);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCLOSECHANNEL;
    }
}
