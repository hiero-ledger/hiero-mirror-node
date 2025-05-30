// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRepositoryTest extends RestJavaIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void findByAlias() {
        var entity = domainBuilder.entity().persist();
        byte[] alias = entity.getAlias();
        var entityDeleted =
                domainBuilder.entity().customize(b -> b.deleted(true)).persist();
        var entityDeletedNull =
                domainBuilder.entity().customize(b -> b.deleted(null)).persist();

        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());
        assertThat(entityRepository.findByAlias(entityDeleted.getAlias())).isEmpty();
        assertThat(entityRepository.findByAlias(entityDeletedNull.getAlias())).isEmpty();
    }

    @Test
    void findByEvmAddress() {
        var entity = domainBuilder.entity().persist();
        var entityDeleted =
                domainBuilder.entity().customize(b -> b.deleted(true)).persist();
        var entityDeletedNull =
                domainBuilder.entity().customize(b -> b.deleted(null)).persist();

        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity.getId());
        assertThat(entityRepository.findByEvmAddress(entityDeleted.getEvmAddress()))
                .isEmpty();
        assertThat(entityRepository.findByEvmAddress(new byte[] {1, 2, 3})).isEmpty();
        assertThat(entityRepository.findByEvmAddress(entityDeletedNull.getEvmAddress()))
                .isEmpty();
    }

    @Test
    void findById() {
        var entity = domainBuilder.entity(-2, domainBuilder.timestamp()).persist();
        assertThat(entityRepository.findById(entity.getId())).contains(entity);
    }
}
