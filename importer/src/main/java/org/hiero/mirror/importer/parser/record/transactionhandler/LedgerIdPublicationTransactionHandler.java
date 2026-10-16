// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
final class LedgerIdPublicationTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    protected void doUpdateTransaction(final Transaction transaction, final RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final var ledgerIdPublication = recordItem.getTransactionBody().getLedgerIdPublication();
        final var ledger = Ledger.builder()
                .ledgerId(DomainUtils.toBytes(ledgerIdPublication.getLedgerId()))
                .timestampRange(Range.atLeast(recordItem.getConsensusTimestamp()))
                .build();
        entityListener.onLedger(ledger);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.LEDGERIDPUBLICATION;
    }
}
