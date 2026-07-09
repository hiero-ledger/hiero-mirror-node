// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.downloader.block.tss;

import com.hedera.cryptography.tss.TSS;
import com.hedera.cryptography.wraps.WRAPSVerificationKey;
import jakarta.inject.Named;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.hiero.mirror.common.domain.tss.Ledger;
import org.hiero.mirror.importer.exception.SignatureVerificationException;
import org.hiero.mirror.importer.repository.LedgerRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@CustomLog
@Named
@NullMarked
@RequiredArgsConstructor
final class TssVerifierImpl implements TssVerifier {

    private static final Ledger EMPTY = new Ledger();

    private final AtomicReference<Optional<Ledger>> ledger = new AtomicReference<>(Optional.empty());
    private final LedgerRepository ledgerRepository;

    private volatile @Nullable Ledger ledgerConfig;
    private volatile @Nullable Ledger ledgerOnChain;

    @Override
    public void setLedger(final Ledger ledger, final boolean fromConfig) {
        if (fromConfig) {
            ledgerConfig = ledger;
        } else {
            ledgerOnChain = ledger;
        }

        // Clear the atomic reference to reload the ledger
        this.ledger.set(Optional.empty());
    }

    @Override
    public void verify(final long blockNumber, final byte[] message, final byte[] signature) {
        final var ledgerId = getLedger().getLedgerId();
        if (!TSS.verifyTSS(ledgerId, signature, message)) {
            if (log.isDebugEnabled()) {
                log.debug(
                        "Failed to verify TSS signature for block {}: ledgerId={}, message={}, signature={}",
                        blockNumber,
                        Hex.encodeHexString(ledgerId),
                        Hex.encodeHexString(message),
                        Hex.encodeHexString(signature));
            }

            throw new SignatureVerificationException("TSS signature verification failed for block " + blockNumber);
        }
    }

    private Ledger getLedger() {
        return Objects.requireNonNull(ledger.get())
                .or(() -> {
                    final var resolved = Optional.ofNullable(ledgerOnChain)
                            .or(ledgerRepository::findTopByOrderByConsensusTimestampDesc)
                            .or(() -> Optional.ofNullable(ledgerConfig))
                            .map(l -> {
                                onLedgerSet(l);
                                return l;
                            })
                            .or(() -> Optional.of(EMPTY));
                    ledger.compareAndSet(Optional.empty(), resolved);
                    return resolved;
                })
                .filter(l -> l != EMPTY)
                .orElseThrow(() -> new IllegalStateException(
                        "Ledger id, history proof verification key and node contributions not found"));
    }

    private void onLedgerSet(final Ledger ledger) {
        final int addressBookSize = ledger.getNodeContributions().size();
        final byte[][] schnorrPublicKeys = new byte[addressBookSize][];
        final long[] nodeIds = new long[addressBookSize];
        final long[] weights = new long[addressBookSize];
        for (int i = 0; i < addressBookSize; i++) {
            final var nodeContribution = ledger.getNodeContributions().get(i);
            schnorrPublicKeys[i] = nodeContribution.getHistoryProofKey();
            nodeIds[i] = nodeContribution.getNodeId();
            weights[i] = nodeContribution.getWeight();
        }

        TSS.setAddressBook(schnorrPublicKeys, weights, nodeIds);
        WRAPSVerificationKey.setCurrentKey(ledger.getHistoryProofVerificationKey());
    }
}
