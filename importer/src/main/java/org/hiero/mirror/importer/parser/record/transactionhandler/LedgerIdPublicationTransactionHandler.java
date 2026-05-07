// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.transactionhandler;

import jakarta.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.transaction.RecordItem;
import org.hiero.mirror.common.domain.transaction.Transaction;
import org.hiero.mirror.common.domain.transaction.TransactionType;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.downloader.block.tss.LedgerIdPublicationTransactionParser;
import org.hiero.mirror.importer.parser.record.entity.EntityListener;
import org.jspecify.annotations.NullMarked;

@CustomLog
@Named
@NullMarked
@RequiredArgsConstructor
final class LedgerIdPublicationTransactionHandler extends AbstractTransactionHandler {

    private static final String NETWORK_LEDGER_DIR = "networkledger";

    private final EntityListener entityListener;
    private final ImporterProperties importerProperties;
    private final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser;

    @Override
    protected void doUpdateTransaction(final Transaction transaction, final RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        final var body = recordItem.getTransactionBody().getLedgerIdPublication();
        final var ledger = ledgerIdPublicationTransactionParser.parse(recordItem.getConsensusTimestamp(), body);
        entityListener.onLedger(ledger);
        saveNetworkLedger(body.toByteArray());
    }

    private void saveNetworkLedger(final byte[] bytes) {
        try {
            final var dir = importerProperties.getDataPath().resolve(NETWORK_LEDGER_DIR);
            Files.createDirectories(dir);
            final var path = dir.resolve(importerProperties.getNetwork());
            Files.write(path, bytes);
            log.info("Saved network ledger to {}", path);
        } catch (IOException e) {
            log.warn("Failed to save network ledger", e);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.LEDGERIDPUBLICATION;
    }
}
