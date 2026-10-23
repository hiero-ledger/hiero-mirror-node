// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the {@code clpr_endpoint_manifest_value} singleton state change off {@code StateChangeContext} and
 * populates {@link org.hiero.mirror.common.domain.transaction.RecordItem}'s CLPR Endpoint Manifest field.
 * {@code ClprMaintenance} is a system-initiated transaction with no meaningful body of its own -- the finalized
 * manifest only exists in its state change.
 */
@Named
final class ClprMaintenanceTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        blockTransaction
                .getStateChangeContext()
                .getClprEndpointManifest()
                .ifPresent(blockTransactionTransformation.recordItemBuilder()::clprEndpointManifest);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRMAINTENANCE;
    }
}
