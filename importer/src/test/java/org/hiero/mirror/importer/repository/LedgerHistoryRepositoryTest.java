// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class LedgerHistoryRepositoryTest extends ImporterIntegrationTest {

    private final LedgerHistoryRepository repository;

    @Test
    void prune() {
        // given
        domainBuilder.ledgerHistory().persist();
        var ledgerHistory2 = domainBuilder.ledgerHistory().persist();
        var ledgerHistory3 = domainBuilder.ledgerHistory().persist();

        // when
        repository.prune(ledgerHistory2.getTimestampUpper());

        // then
        assertThat(repository.findAll()).containsExactly(ledgerHistory3);
    }

    @Test
    void save() {
        var ledgerHistory = domainBuilder.ledgerHistory().persist();
        assertThat(repository.findById(ledgerHistory.getLedgerId())).contains(ledgerHistory);
    }
}
