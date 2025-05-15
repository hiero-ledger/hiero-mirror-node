// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityStakeHistoryRepositoryTest extends ImporterIntegrationTest {

    private final EntityStakeHistoryRepository repository;

    @Test
    void prune() {
        domainBuilder.entityStakeHistory().persist();
        var entityStakeHistory2 = domainBuilder.entityStakeHistory().persist();
        var entityStakeHistory3 = domainBuilder.entityStakeHistory().persist();
        repository.prune(entityStakeHistory2.getTimestampUpper());
        assertThat(repository.findAll()).containsExactly(entityStakeHistory3);
    }

    @Test
    void save() {
        var entityStakeHistory = domainBuilder.entityStakeHistory().get();
        repository.save(entityStakeHistory);
        assertThat(repository.findAll()).containsExactly(entityStakeHistory);
    }
}
