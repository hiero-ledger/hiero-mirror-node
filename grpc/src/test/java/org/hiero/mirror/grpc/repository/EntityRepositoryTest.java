// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.grpc.GrpcIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRepositoryTest extends GrpcIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void findById() {
        // given an entity with every column set to a non-default value
        final var entity = domainBuilder
                .entity(EntityId.of(1L, 2L, domainBuilder.number()), domainBuilder.timestamp())
                .customize(e -> e.declineReward(true)
                        .deleted(true)
                        .permanentRemoval(true)
                        .stakedAccountId(domainBuilder.id())
                        .type(EntityType.TOPIC))
                .persist();
        final long id = entity.getId();

        // when, then
        assertThat(entityRepository.findById(id)).contains(entity);
    }

    @Test
    void findByIdNotFound() {
        assertThat(entityRepository.findById(domainBuilder.id())).isEmpty();
    }
}
