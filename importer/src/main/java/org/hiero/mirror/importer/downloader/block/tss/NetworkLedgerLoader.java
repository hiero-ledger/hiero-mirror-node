// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import jakarta.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@CustomLog
@Named
@NullMarked
@RequiredArgsConstructor
final class NetworkLedgerLoader {

    private final BlockProperties blockProperties;
    private final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser;

    @EventListener(ApplicationReadyEvent.class)
    void load() {
        final var path = blockProperties.getInitialLedgerIdPublication();
        if (path == null) {
            log.info("No initialLedgerIdPublication configured; skipping network ledger load");
            return;
        }

        loadFromPath(path);
    }

    private void loadFromPath(final Path path) {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read initialLedgerIdPublication file: " + path, e);
        }

        final LedgerIdPublicationTransactionBody body;
        try {
            body = LedgerIdPublicationTransactionBody.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Failed to parse initialLedgerIdPublication file: " + path, e);
        }

        final var ledger = ledgerIdPublicationTransactionParser.parse(0L, body);
        blockProperties.setLedger(LedgerProperties.from(ledger));
        log.info("Loaded initial ledger from {}", path);
    }
}
