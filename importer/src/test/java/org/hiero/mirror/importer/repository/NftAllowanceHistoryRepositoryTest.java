// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.NftAllowanceHistory;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NftAllowanceHistoryRepositoryTest extends ImporterIntegrationTest {

    private final NftAllowanceHistoryRepository nftAllowanceHistoryRepository;

    @Test
    void prune() {
        domainBuilder.nftAllowanceHistory().persist();
        var nftAllowanceHistory2 = domainBuilder.nftAllowanceHistory().persist();
        var nftAllowanceHistory3 = domainBuilder.nftAllowanceHistory().persist();

        nftAllowanceHistoryRepository.prune(nftAllowanceHistory2.getTimestampUpper());

        assertThat(nftAllowanceHistoryRepository.findAll()).containsExactly(nftAllowanceHistory3);
    }

    @Test
    void save() {
        NftAllowanceHistory nftAllowanceHistory =
                domainBuilder.nftAllowanceHistory().persist();
        assertThat(nftAllowanceHistoryRepository.findById(nftAllowanceHistory.getId()))
                .get()
                .isEqualTo(nftAllowanceHistory);
    }
}
