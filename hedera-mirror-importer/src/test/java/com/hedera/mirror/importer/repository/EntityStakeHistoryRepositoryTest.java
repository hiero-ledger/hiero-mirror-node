// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
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
