// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.tss.legacy.LedgerIdPublicationTransactionBody;
import jakarta.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.tss.LedgerNodeContribution;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterProperties;
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

    private final BlockProperties blockProperties;
    private final ImporterProperties importerProperties;
    private final ResourceLoader resourceLoader;

    @EventListener(ApplicationReadyEvent.class)
    void load() {
        if (blockProperties.getLedger() != null) {
            log.info("Skipping network ledger load; block.ledger config already set explicitly");
            return;
        }

        final var overridePath = blockProperties.getInitialLedgerIdPublication();
        if (overridePath != null) {
            loadFromPath(overridePath);
            return;
        }

        loadFromClasspath();
    }

    private void loadFromPath(final Path path) {
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read initialLedgerIdPublication file: " + path, e);
        }

        final var ledgerProperties = toLedgerProperties(bytes, "initialLedgerIdPublication file: " + path);
        blockProperties.setLedger(ledgerProperties);
        log.info("Loaded initial ledger from {}", path);
    }

    private void loadFromClasspath() {
        final var location = CLASSPATH_LOCATION_PREFIX + importerProperties.getNetwork();
        final Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("No bundled network ledger found at {}; skipping", location);
            return;
        }

        final byte[] bytes;
        try (var in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundled network ledger: " + location, e);
        }

        if (bytes.length == 0) {
            log.info("Bundled network ledger {} is empty; skipping", location);
            return;
        }

        final var ledgerProperties = toLedgerProperties(bytes, "bundled network ledger: " + location);
        blockProperties.setLedger(ledgerProperties);
        log.info("Loaded initial ledger from {}", location);
    }

    private LedgerProperties toLedgerProperties(final byte[] bytes, final String source) {
        final LedgerIdPublicationTransactionBody body;
        try {
            body = LedgerIdPublicationTransactionBody.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Failed to parse " + source, e);
        }

        final var protoContributions = body.getNodeContributionsList();
        final List<LedgerNodeContribution> nodeContributions = new ArrayList<>(protoContributions.size());
        for (final var nc : protoContributions) {
            nodeContributions.add(LedgerNodeContribution.builder()
                    .historyProofKey(DomainUtils.toBytes(nc.getHistoryProofKey()))
                    .nodeId(nc.getNodeId())
                    .weight(nc.getWeight())
                    .build());
        }

        return LedgerProperties.builder()
                .historyProofVerificationKey(DomainUtils.toBytes(body.getHistoryProofVerificationKey()))
                .ledgerId(DomainUtils.toBytes(body.getLedgerId()))
                .nodeContributions(nodeContributions)
                .build();
    }
}
