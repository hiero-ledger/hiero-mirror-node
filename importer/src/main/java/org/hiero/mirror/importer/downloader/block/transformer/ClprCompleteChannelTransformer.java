// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the verifier-derived {@code clpr_channel_value} state change ({@code trust_anchor}, {@code channel_context},
 * {@code endpoint_manifest_version}, etc.) off {@code StateChangeContext} and populates {@link
 * org.hiero.mirror.common.domain.transaction.RecordItem}'s new CLPR channel field, since neither
 * {@code TransactionRecord} nor {@code TransactionSidecarRecord} has a slot for this data.
 */
@Named
final class ClprCompleteChannelTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var transactionBody =
                blockTransactionTransformation.getTransactionBody().getClprCompleteChannel();
        blockTransaction
                .getStateChangeContext()
                .getClprChannel(transactionBody.getChannelId())
                .ifPresent(blockTransactionTransformation.recordItemBuilder()::clprChannel);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCOMPLETECHANNEL;
    }
}
