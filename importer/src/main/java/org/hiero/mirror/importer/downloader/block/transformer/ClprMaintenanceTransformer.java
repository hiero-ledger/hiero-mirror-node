// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads whichever of the {@code clpr_endpoint_manifest_value}/{@code clpr_ledger_configuration_value} singleton
 * state changes are present off {@code StateChangeContext} and populates
 * {@link org.hiero.mirror.common.domain.transaction.RecordItem}'s corresponding CLPR fields. {@code ClprMaintenance}
 * is a system-initiated transaction with no meaningful body of its own -- a single firing can touch either
 * singleton, both, or (for {@code GENESIS_INITIALIZATION}) create the ledger configuration's very first row, so each
 * must be checked independently rather than assuming a fixed shape.
 */
@Named
final class ClprMaintenanceTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        final var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        final var stateChangeContext = blockTransaction.getStateChangeContext();
        final var clprContextBuilder =
                blockTransactionTransformation.recordItemBuilder().clprContextBuilder();

        final var endpointManifestUpdate = stateChangeContext.getClprEndpointManifestUpdate();
        if (endpointManifestUpdate != null) {
            clprContextBuilder.clprEndpointManifestUpdate(endpointManifestUpdate);
        }

        final var ledgerConfigurationUpdate = stateChangeContext.getClprLedgerConfigurationUpdate();
        if (ledgerConfigurationUpdate != null) {
            clprContextBuilder.clprLedgerConfigurationUpdate(ledgerConfigurationUpdate);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRMAINTENANCE;
    }
}
