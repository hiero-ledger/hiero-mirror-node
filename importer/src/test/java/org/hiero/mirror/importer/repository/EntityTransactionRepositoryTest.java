// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityTransactionRepositoryTest extends ImporterIntegrationTest {

    private final EntityTransactionRepository repository;

    @Test
    void prune() {
        var entityTransaction1 = domainBuilder.entityTransaction().persist();
        var entityTransaction2 = domainBuilder.entityTransaction().persist();
        var entityTransaction3 = domainBuilder.entityTransaction().persist();

        repository.prune(entityTransaction1.getConsensusTimestamp());
        assertThat(repository.findAll()).containsExactlyInAnyOrder(entityTransaction2, entityTransaction3);

        repository.prune(entityTransaction2.getConsensusTimestamp());
        assertThat(repository.findAll()).containsExactly(entityTransaction3);
    }

    @Test
    void save() {
        var entityTransaction = domainBuilder.entityTransaction().get();
        repository.save(entityTransaction);
        assertThat(repository.findById(entityTransaction.getId())).contains(entityTransaction);
    }
}
