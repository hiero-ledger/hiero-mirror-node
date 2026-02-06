// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class LedgerRepositoryTest extends ImporterIntegrationTest {

    private final LedgerRepository repository;

    @Test
    void save() {
        final var ledger = domainBuilder.ledger().get();
        repository.save(ledger);
        assertThat(repository.findById(ledger.getNetwork())).contains(ledger);
        assertThat(repository.findById("dummy")).isEmpty();
    }
}
