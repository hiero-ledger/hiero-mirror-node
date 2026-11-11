// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.StateChangeContext.ClprChannelEntry;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the verifier-derived {@code clpr_channel_value} state change ({@code trust_anchor}, {@code channel_context},
 * {@code endpoint_manifest_version}, etc.) off {@code StateChangeContext} and populates
 * {@link org.hiero.mirror.common.domain.transaction.RecordItem#getClprContext()}'s {@code clprChannels} list, since
 * neither {@code TransactionRecord} nor {@code TransactionSidecarRecord} has a slot for this data.
 */
@Named
final class ClprCompleteChannelTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var transactionBody =
                blockTransactionTransformation.getTransactionBody().getClprCompleteChannel();
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
        return TransactionType.CLPRCOMPLETECHANNEL;
    }
}
