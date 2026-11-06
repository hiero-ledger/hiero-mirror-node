// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class ClprChannelHistoryRepositoryTest extends ImporterIntegrationTest {

    private final ClprChannelHistoryRepository clprChannelHistoryRepository;

    @Test
    void prune() {
        domainBuilder.clprChannelHistory().persist();
        var clprChannelHistory2 = domainBuilder.clprChannelHistory().persist();
        var clprChannelHistory3 = domainBuilder.clprChannelHistory().persist();

        clprChannelHistoryRepository.prune(clprChannelHistory2.getTimestampUpper());

        assertThat(clprChannelHistoryRepository.findAll()).containsExactly(clprChannelHistory3);
    }

    @Test
    void save() {
        var clprChannelHistory = domainBuilder.clprChannelHistory().persist();
        assertThat(clprChannelHistoryRepository.findById(clprChannelHistory.getChannelId()))
                .get()
                .isEqualTo(clprChannelHistory);
    }
}
