// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.CryptoAllowanceHistory;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
