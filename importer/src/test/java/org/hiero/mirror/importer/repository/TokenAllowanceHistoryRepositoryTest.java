// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAllowanceHistoryRepositoryTest extends ImporterIntegrationTest {

    private final TokenAllowanceHistoryRepository tokenAllowanceHistoryRepository;

    @Test
    void prune() {
        domainBuilder.tokenAllowanceHistory().persist();
        var tokenAllowanceHistory2 = domainBuilder.tokenAllowanceHistory().persist();
        var tokenAllowanceHistory3 = domainBuilder.tokenAllowanceHistory().persist();

        tokenAllowanceHistoryRepository.prune(tokenAllowanceHistory2.getTimestampUpper());

        assertThat(tokenAllowanceHistoryRepository.findAll()).containsExactly(tokenAllowanceHistory3);
    }

    @Test
    void save() {
        var tokenAllowanceHistory = domainBuilder.tokenAllowanceHistory().get();
        tokenAllowanceHistoryRepository.save(tokenAllowanceHistory);
        assertThat(tokenAllowanceHistoryRepository.findById(tokenAllowanceHistory.getId()))
                .get()
                .isEqualTo(tokenAllowanceHistory);
    }
}
