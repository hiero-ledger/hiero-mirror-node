// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
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
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var transactionBody =
                blockTransactionTransformation.getTransactionBody().getClprCloseChannel();
        final var channelId = transactionBody.getChannelId();
        final var clprChannelValue = blockTransaction.getStateChangeContext().getClprChannel(channelId);
        final var clprContextBuilder =
                blockTransactionTransformation.recordItemBuilder().clprContextBuilder();
        if (clprChannelValue != null) {
            clprContextBuilder.clprChannels(List.of(new ClprChannelEntry(channelId, clprChannelValue)));
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCLOSECHANNEL;
    }
}
