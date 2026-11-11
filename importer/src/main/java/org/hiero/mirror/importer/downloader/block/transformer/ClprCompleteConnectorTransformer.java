// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import com.hederahashgraph.api.proto.java.ClprConnectorKey;
import jakarta.inject.Named;
import java.util.List;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the {@code clpr_connector_value} state change off {@code StateChangeContext} and populates
 * {@link org.hiero.mirror.common.domain.transaction.RecordItem#getClprContext()}'s
 * {@link org.hiero.mirror.common.domain.transaction.ClprContext#getClprConnector()}.
 */
@Named
final class ClprCompleteConnectorTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var transactionBody =
                blockTransactionTransformation.getTransactionBody().getClprCompleteConnector();
        final var key = ClprConnectorKey.newBuilder()
                .setChannelId(transactionBody.getChannelId())
                .setConnectorId(transactionBody.getConnectorId())
                .build();
        final var clprConnectorValue = blockTransaction.getStateChangeContext().getClprConnector(key);
        final var clprContextBuilder =
                blockTransactionTransformation.recordItemBuilder().clprContextBuilder();
        if (clprConnectorValue != null) {
            clprContextBuilder.clprConnectors(List.of(clprConnectorValue));
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRCOMPLETECONNECTOR;
    }
}
