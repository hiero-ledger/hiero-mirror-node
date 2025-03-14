// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.graphql.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.graphql.GraphqlIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class EntityRepositoryTest extends GraphqlIntegrationTest {

    private final EntityRepository entityRepository;

    @Test
    void find() {
        var entity = domainBuilder.entity(-2, domainBuilder.timestamp()).persist();
        assertThat(entityRepository.findById(entity.getId())).contains(entity);
    }

    @Test
    void findByAlias() {
        var entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findByAlias(entity.getAlias())).get().isEqualTo(entity);
    }

    @Test
    void findByEvmAddress() {
        var entity = domainBuilder.entity().persist();
        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress()))
                .get()
                .isEqualTo(entity);
    }
}
