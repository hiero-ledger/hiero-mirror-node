// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the {@code clpr_connector_value} state change off {@code StateChangeContext} and populates
 * {@link org.hiero.mirror.common.domain.transaction.RecordItem#getClprConnector()}.
 */
@Named
final class ClprCompleteConnectorTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        var transactionBody =
                blockTransactionTransformation.getTransactionBody().getClprCompleteConnector();
        var key = ClprConnectorKey.newBuilder()
                .setChannelId(transactionBody.getChannelId())
                .setConnectorId(transactionBody.getConnectorId())
                .build();
        blockTransaction
                .getStateChangeContext()
                .getClprConnector(key)
                .ifPresent(blockTransactionTransformation.recordItemBuilder()::clprConnector);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCOMPLETECONNECTOR;
    }
}
