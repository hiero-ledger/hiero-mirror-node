// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.AccountBalance.Id;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenTransfer;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenBalanceRepositoryTest extends Web3IntegrationTest {

    private static final long TRANSFER_AMOUNT = 10L;
    private static final long TRANSFER_INCREMENT = 1L;
    private static final EntityId TREASURY_ENTITY_ID = EntityId.of(2);

    private final TokenBalanceRepository tokenBalanceRepository;

    @Test
    void findHistoricalByIdAndTimestampLessThanBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp() + 1))
                .get()
                .isEqualTo(tokenBalance1);
    }

    @Test
    void findHistoricalByIdAndConsensusTimestampEqualToBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp()))
                .get()
                .isEqualTo(tokenBalance1);
    }

    @Test
    void findHistoricalByIdAndConsensusTimestampLessThanBlockTimestamp() {
        var tokenBalance1 = domainBuilder.tokenBalance().persist();

        assertThat(tokenBalanceRepository.findByIdAndTimestampLessThan(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        tokenBalance1.getId().getConsensusTimestamp() - 1))
                .isEmpty();
    }

    @Test
    void shouldNotIncludeBalanceBeforeConsensusTimestamp() {
        var accountBalance = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(
                        accountBalance.getId().getConsensusTimestamp(),
                        accountBalance.getId().getAccountId(),
                        domainBuilder.entityId())))
                .persist();
        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();

        persistTokenTransfersBefore(3, consensusTimestamp, tokenBalance1);

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10))
                .get()
                .isEqualTo(tokenBalance1.getBalance());
    }

    @Test
    void shouldIncludeBalanceDuringValidTimestampRange() {
        var accountBalance = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(
                        accountBalance.getId().getConsensusTimestamp(),
                        accountBalance.getId().getAccountId(),
                        domainBuilder.entityId())))
                .persist();

        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = tokenBalance1.getBalance();

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3;

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void shouldNotIncludeBalanceAfterTimestampFilter() {
        var accountBalance = domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID)))
                .persist();
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(new TokenBalance.Id(
                        accountBalance.getId().getConsensusTimestamp(),
                        accountBalance.getId().getAccountId(),
                        domainBuilder.entityId())))
                .persist();
        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = tokenBalance1.getBalance(); // 1

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3; // 31

        persistTokenTransfers(3, consensusTimestamp + 10, tokenBalance1);

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void findHistoricalBalanceIfTokenBalanceIsMissing() {
        // Test case: account_balance and token_balance entry BEFORE token transfers is missing
        // usually the account_balance/token_balance gets persisted ~8 mins after the account creation

        // not persisted
        var tokenBalance1 = domainBuilder
                .tokenBalance()
                .customize(tb -> tb.id(
                        new TokenBalance.Id(domainBuilder.timestamp(), TREASURY_ENTITY_ID, domainBuilder.entityId())))
                .get();

        long consensusTimestamp = tokenBalance1.getId().getConsensusTimestamp();
        long historicalAccountBalance = 0L; // 0 - we just created this account and there are no transfers

        persistTokenTransfers(3, consensusTimestamp, tokenBalance1);
        historicalAccountBalance += TRANSFER_AMOUNT * 3; // 30

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenBalance1.getId().getTokenId().getId(),
                        tokenBalance1.getId().getAccountId().getId(),
                        consensusTimestamp + 10))
                .get()
                .isEqualTo(historicalAccountBalance);
    }

    @Test
    void findHistoricalTokenBalanceUpToTimestampMissingTokenBalance() {
        var accountId = domainBuilder.entityId();
        var tokenId = domainBuilder.entityId();
        long snapshotTimestamp = domainBuilder.timestamp();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.id(new Id(snapshotTimestamp, TREASURY_ENTITY_ID)))
                .persist();
        var tokenTransfer1 = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 2, tokenId, accountId)))
                .persist();
        var tokenTransfer2 = domainBuilder
                .tokenTransfer()
                .customize(tt -> tt.id(new TokenTransfer.Id(snapshotTimestamp + 3, tokenId, accountId)))
                .persist();

        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenId.getId(), accountId.getId(), snapshotTimestamp))
                .get()
                .isEqualTo(0L);
        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenId.getId(), accountId.getId(), snapshotTimestamp + 1)) // Assumed account creation
                .get()
                .isEqualTo(0L);
        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenId.getId(), accountId.getId(), snapshotTimestamp + 2))
                .get()
                .isEqualTo(tokenTransfer1.getAmount());
        assertThat(tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenId.getId(), accountId.getId(), snapshotTimestamp + 3))
                .get()
                .isEqualTo(tokenTransfer2.getAmount() + tokenTransfer2.getAmount());
    }

    private void persistTokenTransfersBefore(int count, long baseTimestamp, TokenBalance tokenBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp - TRANSFER_INCREMENT * (i + 1);
            persistTokenTransfer(timestamp, tokenBalance1);
        }
    }

    private void persistTokenTransfers(int count, long baseTimestamp, TokenBalance tokenBalance1) {
        for (int i = 0; i < count; i++) {
            long timestamp = baseTimestamp + TRANSFER_INCREMENT * (i + 1);
            persistTokenTransfer(timestamp, tokenBalance1);
        }
    }

    private void persistTokenTransfer(long timestamp, TokenBalance tokenBalance1) {
        domainBuilder
                .tokenTransfer()
                .customize(b -> b.amount(TRANSFER_AMOUNT)
                        .id(new TokenTransfer.Id(
                                timestamp,
                                tokenBalance1.getId().getTokenId(),
                                tokenBalance1.getId().getAccountId()))
                        .payerAccountId(tokenBalance1.getId().getAccountId()))
                .persist();
    }
}
