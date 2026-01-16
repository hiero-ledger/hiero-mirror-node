// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.CryptoAllowanceHistory;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CryptoAllowanceHistoryRepositoryTest extends ImporterIntegrationTest {

    private final CryptoAllowanceHistoryRepository cryptoAllowanceHistoryRepository;

    @Test
    void prune() {
        domainBuilder.cryptoAllowanceHistory().persist();
        var cryptoAllowanceHistory2 = domainBuilder.cryptoAllowanceHistory().persist();
        var cryptoAllowanceHistory3 = domainBuilder.cryptoAllowanceHistory().persist();

        cryptoAllowanceHistoryRepository.prune(cryptoAllowanceHistory2.getTimestampUpper());

        assertThat(cryptoAllowanceHistoryRepository.findAll()).containsExactly(cryptoAllowanceHistory3);
    }

    @Test
    void save() {
        CryptoAllowanceHistory cryptoAllowanceHistory =
                domainBuilder.cryptoAllowanceHistory().persist();
        assertThat(cryptoAllowanceHistoryRepository.findById(cryptoAllowanceHistory.getId()))
                .get()
                .isEqualTo(cryptoAllowanceHistory);
    }
}
