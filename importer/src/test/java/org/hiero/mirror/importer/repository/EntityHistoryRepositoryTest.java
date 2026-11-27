// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.entity.EntityType.CONTRACT;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.importer.ImporterIntegrationTest;
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
        // A row that is already a contract and one outside the id list must stay unchanged
        var contractHistory =
                domainBuilder.entityHistory().customize(e -> e.type(CONTRACT)).persist();
        var unlistedHistory = domainBuilder.entityHistory().persist();

        assertThat(entityHistoryRepository.updateContractType(
                        List.of(entityHistory.getId(), entityHistory2.getId(), contractHistory.getId())))
                .isEqualTo(2);

        entityHistory.setType(CONTRACT);
        entityHistory2.setType(CONTRACT);
        assertThat(entityHistoryRepository.findAll())
                .containsExactlyInAnyOrder(entityHistory, entityHistory2, contractHistory, unlistedHistory);
    }
}
