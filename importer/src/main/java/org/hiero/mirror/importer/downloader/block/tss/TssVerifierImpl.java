// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import jakarta.inject.Named;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.ledger.Ledger;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.repository.LedgerRepository;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
final class TssVerifierImpl implements TssVerifier {

    private final ImporterProperties importerProperties;
    private final LedgerRepository ledgerRepository;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Optional<Ledger> ledgerStored = loadLedger();

    private Optional<Ledger> ledgerCached = Optional.empty();

    @Override
    public void setLedger(final Ledger ledger) {
        ledgerCached = Optional.of(ledger);
    }

    @Override
    public void verify(final byte[] message, final byte[] signature) {}

    private Optional<Ledger> loadLedger() {
        return ledgerRepository.findById(importerProperties.getNetwork());
    }

    private Ledger getLedger() {
        return ledgerCached
                .or(this::getLedgerStored)
                .orElseThrow(() ->
                        new IllegalStateException("Ledger id, history proof key and node contributions not found"));
    }
}
