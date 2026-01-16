// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance;
import org.hiero.mirror.common.domain.balance.TokenBalance.Id;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class TokenBalanceRepositoryTest extends ImporterIntegrationTest {

    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 0
            1023, 100
            """)
    void balanceSnapshot(long shard, long realm) {
        // given
        commonProperties.setShard(shard);
        commonProperties.setRealm(realm);
        var treasury = systemEntity.treasuryAccount();
        var tokenAccount1 = domainBuilder.tokenAccount().persist();
        var tokenAccount2 = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.associated(false).balance(0))
                .persist();

        // when
        long snapshotTimestamp = domainBuilder.timestamp();
        tokenBalanceRepository.balanceSnapshot(snapshotTimestamp, treasury.getId());

        // then
        var expected = List.of(
                buildTokenBalance(tokenAccount1, snapshotTimestamp),
                buildTokenBalance(tokenAccount2, snapshotTimestamp));
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void balanceSnapshotWithDissociatedTokenAccounts() {
        // given
        long lastSnapshotTimestamp = domainBuilder.timestamp();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(lastSnapshotTimestamp, systemEntity.treasuryAccount())))
                .persist();
        // dissociated before last snapshot, will not appear in full snapshot
        domainBuilder
                .tokenAccount()
                .customize(ta -> ta.associated(false).balance(0).balanceTimestamp(lastSnapshotTimestamp - 1))
                .persist();
        var tokenAccount1 = domainBuilder.tokenAccount().persist();
        var tokenAccount2 = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.associated(false).balance(0))
                .persist();
        long newSnapshotTimestamp = domainBuilder.timestamp();
        // Add treasury account balance at newSnapshotTimestamp to test the corner case that the SQL correctly
        // compares token account balance timestamp against the max treasury account balance timestamp before
        // newSnapshotTimestamp
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(newSnapshotTimestamp, systemEntity.treasuryAccount())))
                .persist();
        var expected = List.of(
                buildTokenBalance(tokenAccount1, newSnapshotTimestamp),
                buildTokenBalance(tokenAccount2, newSnapshotTimestamp));

        // when
        tokenBalanceRepository.balanceSnapshot(
                newSnapshotTimestamp, systemEntity.treasuryAccount().getId());

        // then
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0, 0
            1023, 100
            """)
    void balanceSnapshotDeduplicate(long shard, long realm) {
        long lowerRangeTimestamp = 0L;
        long timestamp = 100;
        commonProperties.setShard(shard);
        commonProperties.setRealm(realm);
        var treasury = systemEntity.treasuryAccount();
        assertThat(tokenBalanceRepository.balanceSnapshotDeduplicate(lowerRangeTimestamp, timestamp, treasury.getId()))
                .isZero();
        assertThat(tokenBalanceRepository.findAll()).isEmpty();

        var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(t -> t.balanceTimestamp(1L))
                .persist();
        var tokenAccount2 = domainBuilder
                .tokenAccount()
                .customize(t -> t.balanceTimestamp(1L))
                .persist();

        var expected = Stream.of(tokenAccount, tokenAccount2)
                .map(t -> buildTokenBalance(t, timestamp))
                .collect(Collectors.toList());

        // Update Balance Snapshot includes all balances
        assertThat(tokenBalanceRepository.balanceSnapshotDeduplicate(lowerRangeTimestamp, timestamp, treasury.getId()))
                .isEqualTo(expected.size());
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        long timestamp2 = 200;
        tokenAccount.setBalance(tokenAccount.getBalance() + 1);
        tokenAccount.setBalanceTimestamp(timestamp2);
        tokenAccountRepository.save(tokenAccount);
        expected.add(buildTokenBalance(tokenAccount, timestamp2));

        // Update includes only the updated token account
        assertThat(tokenBalanceRepository.balanceSnapshotDeduplicate(timestamp, timestamp2, treasury.getId()))
                .isOne();
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        long timestamp3 = 300;
        tokenAccount2.setBalance(tokenAccount2.getBalance() + 1);
        tokenAccount2.setBalanceTimestamp(timestamp3);
        tokenAccountRepository.save(tokenAccount2);
        expected.add(buildTokenBalance(tokenAccount2, timestamp3));
        var tokenAccount3 = domainBuilder
                .tokenAccount()
                .customize(t -> t.balanceTimestamp(timestamp3))
                .persist();
        expected.add(buildTokenBalance(tokenAccount3, timestamp3));

        // Update includes only the token accounts with a balance timestamp greater than the max timestamp
        assertThat(tokenBalanceRepository.balanceSnapshotDeduplicate(timestamp2, timestamp3, treasury.getId()))
                .isEqualTo(2);
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);

        long timestamp4 = 400;
        tokenAccount.setBalance(tokenAccount.getBalance() + 1);
        tokenAccount.setBalanceTimestamp(timestamp4);
        tokenAccountRepository.save(tokenAccount);
        // Update with no change as the update happens at a timestamp equal to the max consensus timestamp
        assertThat(tokenBalanceRepository.balanceSnapshotDeduplicate(timestamp4, timestamp4, treasury.getId()))
                .isZero();
        assertThat(tokenBalanceRepository.findAll()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void save() {
        var tokenBalance1 = domainBuilder.tokenBalance().get();
        var tokenBalance2 = domainBuilder.tokenBalance().get();
        var tokenBalance3 = domainBuilder.tokenBalance().get();

        tokenBalanceRepository.saveAll(List.of(tokenBalance1, tokenBalance2, tokenBalance3));
        assertThat(tokenBalanceRepository.findById(tokenBalance1.getId())).contains(tokenBalance1);
        assertThat(tokenBalanceRepository.findAll())
                .containsExactlyInAnyOrder(tokenBalance1, tokenBalance2, tokenBalance3);
    }

    private TokenBalance buildTokenBalance(TokenAccount tokenAccount, long timestamp) {
        return TokenBalance.builder()
                .balance(tokenAccount.getBalance())
                .id(new Id(timestamp, EntityId.of(tokenAccount.getAccountId()), EntityId.of(tokenAccount.getTokenId())))
                .build();
    }
}
