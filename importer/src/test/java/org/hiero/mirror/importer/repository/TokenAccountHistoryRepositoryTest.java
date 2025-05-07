// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAccountHistoryRepositoryTest extends ImporterIntegrationTest {

    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;

    @Test
    void prune() {
        domainBuilder.tokenAccountHistory().persist();
        var tokenAccountHistory2 = domainBuilder.tokenAccountHistory().persist();
        var tokenAccountHistory3 = domainBuilder.tokenAccountHistory().persist();

        tokenAccountHistoryRepository.prune(tokenAccountHistory2.getTimestampUpper());

        assertThat(tokenAccountHistoryRepository.findAll()).containsExactly(tokenAccountHistory3);
    }

    @Test
    void save() {
        var tokenAccountHistory = domainBuilder.tokenAccountHistory().get();
        tokenAccountHistoryRepository.save(tokenAccountHistory);
        assertThat(tokenAccountHistoryRepository.findById(tokenAccountHistory.getId()))
                .get()
                .isEqualTo(tokenAccountHistory);
    }
}
