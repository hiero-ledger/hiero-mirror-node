// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
final class LedgerIdPublicationTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser;

    @Override
    protected void doUpdateTransaction(final Transaction transaction, final RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final var ledger = ledgerIdPublicationTransactionParser.parse(
                recordItem.getConsensusTimestamp(),
                recordItem.getTransactionBody().getLedgerIdPublication());
        entityListener.onLedger(ledger);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.LEDGERIDPUBLICATION;
    }
}
