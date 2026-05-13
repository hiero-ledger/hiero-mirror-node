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
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.downloader.block.BlockProperties;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
        final var path = blockProperties.getInitialLedgerIdPublication();
        final var loaded = path != null ? loadFromPath(path) : loadFromClasspath();
        if (loaded != null) {
            blockProperties.setLedger(loaded);
        }
    }

    private @Nullable Ledger loadFromPath(final Path path) {
        final LedgerIdPublicationTransactionBody body;
        try (var in = Files.newInputStream(path)) {
            body = LedgerIdPublicationTransactionBody.parseFrom(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse initialLedgerIdPublication file: " + path, e);
        }
        log.info("Loaded initial ledger from {}", path);
        return ledgerIdPublicationTransactionParser.parse(0L, body);
    }

    private @Nullable Ledger loadFromClasspath() {
        final var network = importerProperties.getNetwork();
        if (!BUNDLED_NETWORKS.contains(network)) {
            log.info("No bundled network ledger for network {}; skipping", network);
            return null;
        }

        final var location = CLASSPATH_LOCATION_PREFIX + network;
        final var resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("Bundled network ledger {} not found on classpath; skipping", location);
            return null;
        }

        final LedgerIdPublicationTransactionBody body;
        try (var in = resource.getInputStream()) {
            body = LedgerIdPublicationTransactionBody.parseFrom(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse bundled network ledger: " + location, e);
        }
        log.info("Loaded initial ledger from {}", location);
        return ledgerIdPublicationTransactionParser.parse(0L, body);
    }
}
