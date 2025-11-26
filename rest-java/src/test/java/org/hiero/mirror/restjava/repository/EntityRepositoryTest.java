// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityId;
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
        assertThat(result.getUnreleasedSupply()).isEqualTo(3_500_000L);
        assertThat(result.getConsensusTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void getSupplyHistory() {
        // given
        final var timestamp1 = 1_600_000_000_000_000_000L;
        final var timestamp2 = 1_700_000_000_000_000_000L;
        final var timestamp3 = 1_800_000_000_000_000_000L;
        final var account1 = domainBuilder.entityNum(2L);
        final var account2 = domainBuilder.entityNum(42L);

        createAccountBalance(account1, 1_000_000L, timestamp1);
        createAccountBalance(account2, 2_000_000L, timestamp1);
        createAccountBalance(account1, 1_500_000L, timestamp2);
        createAccountBalance(account2, 2_500_000L, timestamp2);
        createAccountBalance(account1, 3_000_000L, timestamp3);

        final var accountIds = List.of(account1.getId(), account2.getId());

        // when / then
        assertThat(entityRepository.getSupplyHistory(accountIds, timestamp1, timestamp2))
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getUnreleasedSupply()).isEqualTo(4_000_000L);
                    assertThat(r.getConsensusTimestamp()).isEqualTo(timestamp2);
                });
        assertThat(entityRepository.getSupplyHistory(accountIds, timestamp1, timestamp3))
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getUnreleasedSupply()).isEqualTo(5_500_000L);
                    assertThat(r.getConsensusTimestamp()).isEqualTo(timestamp3);
                });
        assertThat(entityRepository.getSupplyHistory(List.of(), 0L, Long.MAX_VALUE))
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getUnreleasedSupply()).isZero();
                    assertThat(r.getConsensusTimestamp()).isZero();
                });
    }

    private Entity createEntityWithBalance(long accountNum, long balance, long balanceTimestamp) {
        final var accountId = domainBuilder.entityNum(accountNum);
        return domainBuilder
                .entity()
                .customize(e -> e.id(accountId.getId()).balance(balance).balanceTimestamp(balanceTimestamp))
                .persist();
    }

    private void createAccountBalance(EntityId accountId, long balance, long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(balance).id(new AccountBalance.Id(timestamp, accountId)))
                .persist();
    }
}
