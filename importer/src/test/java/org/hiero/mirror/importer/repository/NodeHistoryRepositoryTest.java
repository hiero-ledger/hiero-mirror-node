// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class NodeHistoryRepositoryTest extends ImporterIntegrationTest {

    private final NodeHistoryRepository nodeHistoryRepository;

    @Test
    void prune() {
        domainBuilder.nodeHistory().persist();
        var nodeHistory2 = domainBuilder.nodeHistory().persist();
        var nodeHistory3 = domainBuilder.nodeHistory().persist();

        nodeHistoryRepository.prune(nodeHistory2.getTimestampUpper());

        assertThat(nodeHistoryRepository.findAll()).containsExactly(nodeHistory3);
    }

    @Test
    void save() {
        var nodeHistory = domainBuilder.nodeHistory().persist();
        assertThat(nodeHistoryRepository.findById(nodeHistory.getNodeId()))
                .get()
                .isEqualTo(nodeHistory);
    }
}
