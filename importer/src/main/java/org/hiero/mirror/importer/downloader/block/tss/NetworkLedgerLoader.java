// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import jakarta.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@CustomLog
@Named
@NullMarked
@RequiredArgsConstructor
final class NetworkLedgerLoader {

    private static final String CLASSPATH_LOCATION_PREFIX = "classpath:/networkledger/";
    private static final Set<String> BUNDLED_NETWORKS = Set.of(HederaNetwork.MAINNET, HederaNetwork.TESTNET);

    private final BlockProperties blockProperties;
    private final ImporterProperties importerProperties;
    private final LedgerIdPublicationTransactionParser ledgerIdPublicationTransactionParser;
    private final ResourceLoader resourceLoader;

    @EventListener(ApplicationReadyEvent.class)
    void load() {
        if (blockProperties.getLedger() != null) {
            log.info("Skipping network ledger load; block.ledger config already set explicitly");
            return;
        }

        final var path = blockProperties.getInitialLedgerIdPublication();
        if (path != null) {
            loadFromPath(path);
            return;
        }

        loadFromClasspath();
    }

    private void loadFromPath(final Path path) {
        final LedgerIdPublicationTransactionBody body;
        try (var in = Files.newInputStream(path)) {
            body = LedgerIdPublicationTransactionBody.parseFrom(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse initialLedgerIdPublication file: " + path, e);
        }

        applyLedger(body);
        log.info("Loaded initial ledger from {}", path);
    }

    private void loadFromClasspath() {
        final var network = importerProperties.getNetwork();
        if (!BUNDLED_NETWORKS.contains(network)) {
            log.info("No bundled network ledger for network {}; skipping", network);
            return;
        }

        final var location = CLASSPATH_LOCATION_PREFIX + network;
        final Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("Bundled network ledger {} not found on classpath; skipping", location);
            return;
        }

        final LedgerIdPublicationTransactionBody body;
        try (var in = resource.getInputStream()) {
            body = LedgerIdPublicationTransactionBody.parseFrom(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse bundled network ledger: " + location, e);
        }

        applyLedger(body);
        log.info("Loaded initial ledger from {}", location);
    }

    private void applyLedger(final LedgerIdPublicationTransactionBody body) {
        final var ledger = ledgerIdPublicationTransactionParser.parse(0L, body);
        blockProperties.setLedger(LedgerProperties.from(ledger));
    }
}
