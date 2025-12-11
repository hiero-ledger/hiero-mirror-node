// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class AccountBalanceRepositoryTest extends RestJavaIntegrationTest {

    private final AccountBalanceRepository accountBalanceRepository;

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
        assertThat(accountBalanceRepository.getSupplyHistory(accountIds, timestamp1, timestamp2))
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.unreleasedSupply()).isEqualTo(4_000_000L);
                    assertThat(r.consensusTimestamp()).isEqualTo(timestamp2);
                });
        assertThat(accountBalanceRepository.getSupplyHistory(accountIds, timestamp1, timestamp3))
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.unreleasedSupply()).isEqualTo(5_500_000L);
                    assertThat(r.consensusTimestamp()).isEqualTo(timestamp3);
                });
        assertThat(accountBalanceRepository.getSupplyHistory(List.of(), 0L, Long.MAX_VALUE))
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.unreleasedSupply()).isZero();
                    assertThat(r.consensusTimestamp()).isZero();
                });
    }

    private void createAccountBalance(EntityId accountId, long balance, long timestamp) {
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(balance).id(new AccountBalance.Id(timestamp, accountId)))
                .persist();
    }
}
