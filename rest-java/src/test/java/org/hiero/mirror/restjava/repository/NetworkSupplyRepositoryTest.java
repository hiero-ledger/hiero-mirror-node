// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.hiero.mirror.restjava.jooq.domain.Tables;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class NetworkSupplyRepositoryTest extends RestJavaIntegrationTest {

    private final NetworkSupplyRepositoryCustom networkSupplyRepository;

    @Test
    void getSupplyFromEntity() {
        // given
        var entity1Balance = 1_000_000_000L;
        var entity2Balance = 2_000_000_000L;
        var timestamp = domainBuilder.timestamp();

        domainBuilder
                .entity()
                .customize(e -> e.id(2L).balance(entity1Balance).balanceTimestamp(timestamp))
                .persist();
        domainBuilder
                .entity()
                .customize(e -> e.id(100L).balance(entity2Balance).balanceTimestamp(timestamp + 1))
                .persist();

        // when
        var result = networkSupplyRepository.getSupply(Bound.EMPTY);

        // then
        var expectedUnreleased = BigInteger.valueOf(entity1Balance + entity2Balance);
        var expectedReleased = NetworkSupply.TOTAL_SUPPLY.subtract(expectedUnreleased);
        assertThat(result.releasedSupply()).isEqualTo(expectedReleased.toString());
        assertThat(result.totalSupply()).isEqualTo(NetworkSupply.TOTAL_SUPPLY.toString());
        assertThat(result.consensusTimestamp()).isEqualTo(timestamp + 1); // max timestamp
    }

    @Test
    void getSupplyFromEntityNoAccounts() {
        // when, then
        assertThatThrownBy(() -> networkSupplyRepository.getSupply(Bound.EMPTY))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Network supply not found");
    }

    @Test
    void getSupplyFromAccountBalance() {
        // given
        var account2Balance1 = 500_000_000L;
        var account2Balance2 = 600_000_000L;
        var account100Balance = 1_500_000_000L;
        var timestamp1 = domainBuilder.timestamp();
        var timestamp2 = timestamp1 + 1000;
        var timestamp3 = timestamp1 + 2000;

        // Create account balances at different timestamps
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                timestamp1, EntityId.of(2L)))
                        .balance(account2Balance1))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                timestamp2, EntityId.of(2L)))
                        .balance(account2Balance2))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                timestamp2, EntityId.of(100L)))
                        .balance(account100Balance))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                timestamp3, EntityId.of(2L)))
                        .balance(account2Balance2 + 100))
                .persist();

        // when - query at timestamp2 (should get latest balance for each account at or before timestamp2)
        var bound = new Bound(
                new TimestampParameter[] {
                    new TimestampParameter(org.hiero.mirror.restjava.common.RangeOperator.LTE, timestamp2)
                },
                true,
                org.hiero.mirror.restjava.common.Constants.TIMESTAMP,
                Tables.ACCOUNT_BALANCE.CONSENSUS_TIMESTAMP);
        var result = networkSupplyRepository.getSupply(bound);

        // then - should use timestamp2 balances (DISTINCT ON gets latest per account)
        var expectedUnreleased = BigInteger.valueOf(account2Balance2 + account100Balance);
        var expectedReleased = NetworkSupply.TOTAL_SUPPLY.subtract(expectedUnreleased);
        assertThat(result.releasedSupply()).isEqualTo(expectedReleased.toString());
        assertThat(result.totalSupply()).isEqualTo(NetworkSupply.TOTAL_SUPPLY.toString());
        assertThat(result.consensusTimestamp()).isEqualTo(timestamp2);
    }

    @Test
    void getSupplyFromAccountBalanceNoData() {
        // given
        var futureTimestamp = domainBuilder.timestamp() + 1_000_000_000L;
        var bound = new Bound(
                new TimestampParameter[] {
                    new TimestampParameter(org.hiero.mirror.restjava.common.RangeOperator.LTE, futureTimestamp)
                },
                true,
                org.hiero.mirror.restjava.common.Constants.TIMESTAMP,
                Tables.ACCOUNT_BALANCE.CONSENSUS_TIMESTAMP);

        // when, then - should throw when no account balances match
        assertThatThrownBy(() -> networkSupplyRepository.getSupply(bound))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Network supply not found");
    }

    @Test
    void getSupplyVerifyDistinctOnBehavior() {
        // This test verifies DISTINCT ON returns latest balance per account
        // given
        var account2OldBalance = 100L;
        var account2NewBalance = 200L;
        var account100Balance = 300L;
        var timestamp1 = domainBuilder.timestamp();
        var timestamp2 = timestamp1 + 1000;

        // Account 2 has two balances, account 100 has one
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                timestamp1, EntityId.of(2L)))
                        .balance(account2OldBalance))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                timestamp2, EntityId.of(2L)))
                        .balance(account2NewBalance))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                timestamp2, EntityId.of(100L)))
                        .balance(account100Balance))
                .persist();

        // when
        var bound = new Bound(
                new TimestampParameter[] {
                    new TimestampParameter(org.hiero.mirror.restjava.common.RangeOperator.LTE, timestamp2)
                },
                true,
                org.hiero.mirror.restjava.common.Constants.TIMESTAMP,
                Tables.ACCOUNT_BALANCE.CONSENSUS_TIMESTAMP);
        var result = networkSupplyRepository.getSupply(bound);

        // then - should use NEW balance for account 2 (not old one)
        var expectedUnreleased = BigInteger.valueOf(account2NewBalance + account100Balance);
        var expectedReleased = NetworkSupply.TOTAL_SUPPLY.subtract(expectedUnreleased);
        assertThat(result.releasedSupply()).isEqualTo(expectedReleased.toString());
        assertThat(result.consensusTimestamp()).isEqualTo(timestamp2);
    }

    @Test
    void getSupplyVerifyNullConsensusTimestampCheck() {
        // This test verifies the null check for consensusTimestamp works correctly
        // when max() returns NULL from empty CTE

        // given - create account balances but with timestamp outside query range
        var accountBalance = 1_000_000_000L;
        var earlyTimestamp = 1000L;

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new org.hiero.mirror.common.domain.balance.AccountBalance.Id(
                                earlyTimestamp, EntityId.of(2L)))
                        .balance(accountBalance))
                .persist();

        // when - query with timestamp range that excludes all data
        var futureTimestamp = earlyTimestamp + 1_000_000_000L;
        var bound = new Bound(
                new TimestampParameter[] {
                    new TimestampParameter(org.hiero.mirror.restjava.common.RangeOperator.GT, futureTimestamp)
                },
                true,
                org.hiero.mirror.restjava.common.Constants.TIMESTAMP,
                Tables.ACCOUNT_BALANCE.CONSENSUS_TIMESTAMP);

        // then - should throw EntityNotFoundException when max() returns NULL
        assertThatThrownBy(() -> networkSupplyRepository.getSupply(bound))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Network supply not found");
    }
}
