// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.hook.HookHistory;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class HookHistoryRepositoryTest extends ImporterIntegrationTest {

    private final HookHistoryRepository hookHistoryRepository;

    @Test
    void prune() {
        domainBuilder.hookHistory().persist();
        var hookHistory2 = domainBuilder.hookHistory().persist();
        var hookHistory3 = domainBuilder.hookHistory().persist();

        hookHistoryRepository.prune(hookHistory2.getTimestampUpper());

        assertThat(hookHistoryRepository.findAll()).containsExactly(hookHistory3);
    }

    @Test
    void save() {
        HookHistory hookHistory = domainBuilder.hookHistory().persist();
        assertThat(hookHistoryRepository.findById(hookHistory.getId())).get().isEqualTo(hookHistory);
    }
}
