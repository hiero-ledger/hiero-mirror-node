// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.graphql.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.AbstractEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.graphql.GraphqlIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRepositoryTest extends GraphqlIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void find() {
        // findById doesn't filter out deleted entities, so deleted is also set to a non-default value
        var entity = domainBuilder
                .entity(-2, domainBuilder.timestamp())
                .customize(e -> customize(e.deleted(true)))
                .persist();
        assertThat(entityRepository.findById(entity.getId())).contains(entity);
    }

    @Test
    void findByAlias() {
        var entity = domainBuilder
                .entity(nonZeroShardRealmId(), domainBuilder.timestamp())
                .customize(this::customize)
                .persist();
        assertThat(entityRepository.findByAlias(entity.getAlias())).get().isEqualTo(entity);
    }

    @Test
    void findByEvmAddress() {
        var entity = domainBuilder
                .entity(nonZeroShardRealmId(), domainBuilder.timestamp())
                .customize(this::customize)
                .persist();
        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity);
    }

    // Set the columns DomainBuilder leaves null or false, so a column that silently doesn't map fails the comparison
    private void customize(AbstractEntity.AbstractEntityBuilder<?, ?> builder) {
        builder.declineReward(true).permanentRemoval(true).stakedAccountId(domainBuilder.id());
    }

    // A non-zero shard and realm, so those columns aren't left at 0
    private EntityId nonZeroShardRealmId() {
        return EntityId.of(1L, 2L, domainBuilder.number());
    }
}
