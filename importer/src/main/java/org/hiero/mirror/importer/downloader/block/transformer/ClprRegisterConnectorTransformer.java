// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the {@code clpr_pending_connector_commitments} map-update's own key off {@code StateChangeContext} and
 * populates {@link org.hiero.mirror.common.domain.transaction.RecordItem#getClprConnectorPendingCommitment()}.
 */
@Named
final class ClprRegisterConnectorTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        blockTransaction
                .getStateChangeContext()
                .getClprConnectorPendingCommitment()
                .ifPresent(blockTransactionTransformation.recordItemBuilder()::clprConnectorPendingCommitment);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRREGISTERCONNECTOR;
    }
}
