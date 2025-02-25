// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityHistory;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityHistoryRepositoryTest extends ImporterIntegrationTest {

    private final EntityHistoryRepository entityHistoryRepository;

    @Test
    void prune() {
        domainBuilder.entityHistory().persist();
        var entityHistory2 = domainBuilder.entityHistory().persist();
        var entityHistory3 = domainBuilder.entityHistory().persist();

        entityHistoryRepository.prune(entityHistory2.getTimestampUpper());

        assertThat(entityHistoryRepository.findAll()).containsExactly(entityHistory3);
    }

    @Test
    void save() {
        EntityHistory entityHistory = domainBuilder.entityHistory().persist();
        assertThat(entityHistoryRepository.findById(entityHistory.getId()))
                .get()
                .isEqualTo(entityHistory);
    }

    @Test
    void updateContractType() {
        var entityHistory = domainBuilder.entityHistory().persist();
        var entityHistory2 = domainBuilder.entityHistory().persist();
        entityHistoryRepository.updateContractType(List.of(entityHistory.getId(), entityHistory2.getId()));
        assertThat(entityHistoryRepository.findAll())
                .hasSize(2)
                .extracting(EntityHistory::getType)
                .allMatch(e -> e == CONTRACT);
    }
}
