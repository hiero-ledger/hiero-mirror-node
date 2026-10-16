// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.transformer;

import jakarta.inject.Named;
import org.hiero.mirror.common.domain.transaction.TransactionType;

/**
 * Reads the {@code clpr_ledger_configuration_value} singleton state change off {@code StateChangeContext} and
 * populates {@link org.hiero.mirror.common.domain.transaction.RecordItem}'s CLPR Ledger Configuration field, since
 * the submitted transaction body isn't trustworthy past the first update.
 */
@Named
final class ClprUpdateLedgerConfigurationTransformer extends AbstractBlockTransactionTransformer {

    @Override
    protected void doTransform(BlockTransactionTransformation blockTransactionTransformation) {
        var blockTransaction = blockTransactionTransformation.blockTransaction();
        if (!blockTransaction.isSuccessful()) {
            return;
        }

        blockTransaction
                .getStateChangeContext()
                .getClprLedgerConfiguration()
                .ifPresent(blockTransactionTransformation.recordItemBuilder()::clprLedgerConfiguration);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CLPRUPDATELEDGERCONFIGURATION;
    }
}
