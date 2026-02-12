// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import jakarta.inject.Named;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.importer.repository.LedgerRepository;
import org.jspecify.annotations.NullMarked;

@Named
@NullMarked
@RequiredArgsConstructor
final class TssVerifierImpl implements TssVerifier {

    private static final Ledger EMPTY = new Ledger();

    private final AtomicReference<Optional<Ledger>> ledger = new AtomicReference<>(Optional.empty());
    private final LedgerRepository ledgerRepository;

    @Override
    public void setLedger(final Ledger ledger) {
        this.ledger.set(Optional.of(ledger));
    }

    @Override
    public void verify(final byte[] message, final byte[] signature) {
        // TBA in future PRs
    }

    private Ledger getLedger() {
        return Objects.requireNonNull(ledger.get())
                .or(() -> {
                    final var saved = ledgerRepository
                            .findTopByOrderByConsensusTimestampDesc()
                            .or(() -> Optional.of(EMPTY));
                    ledger.compareAndSet(Optional.empty(), saved);
                    return saved;
                })
                .filter(l -> l != EMPTY)
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger id, history proof verification key and node contributions not found"));
    }
}
