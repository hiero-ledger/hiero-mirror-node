// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
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

    @Test
    void getSupply() {
        // given
        final var timestamp = domainBuilder.timestamp();
        final var account1 = createEntityWithBalance(2L, 1_000_000L, timestamp);
        final var account2 = createEntityWithBalance(42L, 2_000_000L, timestamp);
        final var account3 = createEntityWithBalance(100L, 500_000L, timestamp);
        final var accountIds = List.of(account1.getId(), account2.getId(), account3.getId());

        // when
        final var result = entityRepository.getSupply(accountIds);

        // then
        assertThat(result).isNotNull();
        assertThat(result.unreleasedSupply()).isEqualTo(3_500_000L);
        assertThat(result.consensusTimestamp()).isEqualTo(timestamp);
    }

    private Entity createEntityWithBalance(long accountNum, long balance, long balanceTimestamp) {
        final var accountId = domainBuilder.entityNum(accountNum);
        return domainBuilder
                .entity()
                .customize(e -> e.id(accountId.getId()).balance(balance).balanceTimestamp(balanceTimestamp))
                .persist();
    }
}
