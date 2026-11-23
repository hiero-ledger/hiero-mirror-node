// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

/**
 * Base transformer for CLPR transactions that populate the CLPR context with the resulting channel state change.
 * Skips unsuccessful transactions and leaves the context unchanged when no channel state change exists.
 */
abstract class AbstractClprChannelTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected final void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var channelId = getChannelId(blockTransactionTransformation.getTransactionBody());
        final var entry = blockTransaction.getStateChangeContext().getClprChannelEntry(channelId);
        final var clprContextBuilder =
                blockTransactionTransformation.recordItemBuilder().clprContextBuilder();
        if (entry != null) {
            clprContextBuilder.clprChannels(List.of(entry));
        }
    }

    protected abstract ByteString getChannelId(TransactionBody transactionBody);
}
