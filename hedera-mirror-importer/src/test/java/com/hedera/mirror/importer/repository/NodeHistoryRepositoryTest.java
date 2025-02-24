// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.NodeHistory;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
        NodeHistory nodeHistory = domainBuilder.nodeHistory().persist();
        assertThat(nodeHistoryRepository.findById(nodeHistory.getNodeId()))
                .get()
                .isEqualTo(nodeHistory);
    }
}
