// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AccountBalanceRepositoryTest extends Web3IntegrationTest {

    private final AccountBalanceRepository accountBalanceRepository;

    private static final EntityId TREASURY_ENTITY_ID = EntityId.of(2);
    static final long TRANSFER_AMOUNT = 10L;
    static final long TRANSFER_INCREMENT = 1L;

    @Test
    void findHistoricalByIdAndTimestampLessThanBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(
                        accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp() + 1l))
                .get()
                .isEqualTo(accountBalance);
    }

    @Test
    void findHistoricalByIdAndTimestampEqualToBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(
                        accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp()))
                .get()
                .isEqualTo(accountBalance);
    }

    @Test
    void findHistoricalByIdAndTimestampGreaterThanBlockTimestamp() {
        var accountBalance = domainBuilder.accountBalance().persist();
        assertThat(accountBalanceRepository.findByIdAndTimestampLessThan(
                        accountBalance.getId().getAccountId().getId(),
                        accountBalance.getId().getConsensusTimestamp() - 1l))
                .isEmpty();
    }

    @Test
    void shouldNotIncludeBalanceBeforeConsensusTimestamp() {
        var accountBalance1 = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();
        long consensusTimestamp = accountBalance1.getId().getConsensusTimestamp();

        persistCryptoTransfersBefore(3, consensusTimestamp, accountBalance1);

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountBalance1.getId().getAccountId().getId(), consensusTimestamp + 10L))
                .get()
                .isEqualTo(accountBalance1.getBalance());
    }

    @Test
    void shouldIncludeBalanceDuringValidTimestampRange() {
        var accountBalance1 = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();

        long consensusTimestamp = accountBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = accountBalance1.getBalance();

        persistCryptoTransfers(3, consensusTimestamp, accountBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountBalance1.getId().getAccountId().getId(), consensusTimestamp + 10L))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void shouldNotIncludeBalanceAfterTimestampFilter() {
        var accountBalance1 = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID))
                        .balance(1L))
                .persist();
        long consensusTimestamp = accountBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = accountBalance1.getBalance();

        persistCryptoTransfers(3, consensusTimestamp, accountBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        persistCryptoTransfers(3, consensusTimestamp + 10, accountBalance1);

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountBalance1.getId().getAccountId().getId(), consensusTimestamp + 10l))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void shouldGetBalanceWhenAccountBalanceEntryIsMissingTimestampBeforeTheAccountCreation() {
        // Test case: account_balance entry BEFORE crypto transfers is missing
        // pass timestamp that is before the initial balance transfer and account creation
        long accountId = 123L;
        long initialBalance = 15L;
        Entity account = domainBuilder
                .entity()
                .customize(a -> a.id(accountId).balance(initialBalance))
                .persist();
        long accountCreationTimestamp = account.getCreatedTimestamp();

        var initialTransfer = domainBuilder
                .cryptoTransfer()
                .customize(
                        b -> b.amount(initialBalance).entityId(accountId).consensusTimestamp(accountCreationTimestamp))
                .persist();

        // second transfer
        domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(initialTransfer.getConsensusTimestamp() + 2L))
                .persist();

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, accountCreationTimestamp - 1))
                .get()
                .isEqualTo(0L);
    }

    @Test
    void shouldGetBalanceWhenAccountBalanceEntryIsMissingMatchingTheInitialBalance() {
        // Test case: account_balance entry BEFORE crypto transfers is missing
        // usually the account_balance gets persisted ~8 mins after the account creation
        // pass timestamp that is equal to the initial balance transfer
        long accountId = 123L;
        long initialBalance = 15L;
        Entity account = domainBuilder
                .entity()
                .customize(a -> a.id(accountId).balance(initialBalance))
                .persist();
        long accountCreationTimestamp = account.getCreatedTimestamp();

        // account creation initial transfer
        var initialTransfer = domainBuilder
                .cryptoTransfer()
                .customize(
                        b -> b.amount(initialBalance).entityId(accountId).consensusTimestamp(accountCreationTimestamp))
                .persist();
        var secondTransfer = domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(initialTransfer.getConsensusTimestamp() + 2L))
                .persist();
        long timestampBetweenTheTransfers = initialTransfer.getConsensusTimestamp() + 1L;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestampBetweenTheTransfers))
                .get()
                .isEqualTo(initialBalance);

        domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountId)
                        .consensusTimestamp(secondTransfer.getConsensusTimestamp() + 2L))
                .persist();
        long timestampBetweenSecondAndThirdTheTransfers = secondTransfer.getConsensusTimestamp() + 1L;

        assertThat(accountBalanceRepository.findHistoricalAccountBalanceUpToTimestamp(
                        accountId, timestampBetweenSecondAndThirdTheTransfers))
                .get()
                .isEqualTo(TRANSFER_AMOUNT + initialBalance);
    }

    private void persistCryptoTransfersBefore(int count, long baseTimestamp, AccountBalance accountBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp - TRANSFER_INCREMENT * (i + 1l);
            persistCryptoTransfer(timestamp, accountBalance1);
        }
    }

    private void persistCryptoTransfers(int count, long baseTimestamp, AccountBalance accountBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp + TRANSFER_INCREMENT * (i + 1l);
            persistCryptoTransfer(timestamp, accountBalance1);
        }
    }

    private void persistCryptoTransfer(long timestamp, AccountBalance accountBalance1) {
        domainBuilder
                .cryptoTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .entityId(accountBalance1.getId().getAccountId().getId())
                        .consensusTimestamp(timestamp))
                .persist();
    }
}
