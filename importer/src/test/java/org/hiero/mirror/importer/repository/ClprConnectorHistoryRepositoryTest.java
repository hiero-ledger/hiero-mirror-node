// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprConnectorHistoryRepositoryTest extends ImporterIntegrationTest {

    private final ClprConnectorHistoryRepository clprConnectorHistoryRepository;

    @Test
    void prune() {
        domainBuilder.clprConnectorHistory().persist();
        var clprConnectorHistory2 = domainBuilder.clprConnectorHistory().persist();
        var clprConnectorHistory3 = domainBuilder.clprConnectorHistory().persist();

        clprConnectorHistoryRepository.prune(clprConnectorHistory2.getTimestampUpper());

        assertThat(clprConnectorHistoryRepository.findAll()).containsExactly(clprConnectorHistory3);
    }

    @Test
    void save() {
        var clprConnectorHistory = domainBuilder.clprConnectorHistory().persist();
        assertThat(clprConnectorHistoryRepository.findById(clprConnectorHistory.getId()))
                .get()
                .isEqualTo(clprConnectorHistory);
    }
}
