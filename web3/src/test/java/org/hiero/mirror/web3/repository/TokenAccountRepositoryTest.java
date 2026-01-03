// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.TokenAccount;
import org.hiero.mirror.common.domain.token.TokenFreezeStatusEnum;
import org.hiero.mirror.common.domain.token.TokenKycStatusEnum;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.repository.projections.TokenAccountAssociationsCount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@RequiredArgsConstructor
class TokenAccountRepositoryTest extends Web3IntegrationTest {
    private final int accountId = 123;
    private final TokenAccountRepository repository;

    @CsvSource(textBlock = """
            UNFROZEN, REVOKED, FROZEN, GRANTED, FROZEN, GRANTED
            UNFROZEN, REVOKED, , , UNFROZEN, REVOKED
            FROZEN, NOT_APPLICABLE, , , FROZEN, NOT_APPLICABLE
            NOT_APPLICABLE, NOT_APPLICABLE, , , NOT_APPLICABLE, NOT_APPLICABLE
            """)
    @ParameterizedTest
    void findById(
            TokenFreezeStatusEnum tokenFreezeStatus,
            TokenKycStatusEnum tokenKycStatus,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus) {
        final var token = domainBuilder
                .token()
                .customize(t -> t.freezeStatus(tokenFreezeStatus).kycStatus(tokenKycStatus))
                .persist();
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(
                        a -> a.freezeStatus(freezeStatus).kycStatus(kycStatus).tokenId(token.getTokenId()))
                .persist();

        assertThat(repository.findById(tokenAccount.getId())).hasValueSatisfying(account -> assertThat(account)
                .returns(expectedFreezeStatus, TokenAccount::getFreezeStatus)
                .returns(expectedKycStatus, TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance));
    }

    @CsvSource(textBlock = """
            ,
            FROZEN, GRANTED
            """)
    @ParameterizedTest
    void findByIdMissingToken(TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(a -> a.freezeStatus(freezeStatus).kycStatus(kycStatus))
                .persist();

        assertThat(repository.findById(tokenAccount.getId())).hasValueSatisfying(account -> assertThat(account)
                .returns(freezeStatus, TokenAccount::getFreezeStatus)
                .returns(kycStatus, TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance));
    }

    @Test
    void countByAccountIdAndAssociatedGroupedByBalanceIsPositive() {
        long accId = 22L;
        long nextAccountId = accId + 1L;
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).balance(23).accountId(accId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).balance(24).accountId(accId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).balance(0).accountId(accId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(false).accountId(accId))
                .persist();

        domainBuilder.tokenAccount().customize(a -> a.accountId(nextAccountId)).persist();

        var expected = List.of(tuple(true, 2), tuple(false, 1));
        assertThat(repository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accId))
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrderElementsOf(expected);

        // Verify cached result
        repository.deleteAll();
        assertThat(repository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(accId))
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(repository.countByAccountIdAndAssociatedGroupedByBalanceIsPositive(nextAccountId))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampLessThanBlock() {
        domainBuilder.tokenAccount().persist();
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccount.getId().getAccountId(),
                        tokenAccount.getId().getTokenId(),
                        tokenAccount.getTimestampLower() + 1))
                .get()
                .isEqualTo(tokenAccount);
    }

    @Test
    void findByIdAndTimestampEqualToBlock() {
        domainBuilder.tokenAccount().persist();
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccount.getId().getAccountId(),
                        tokenAccount.getId().getTokenId(),
                        tokenAccount.getTimestampLower()))
                .get()
                .isEqualTo(tokenAccount);
    }

    @Test
    void findByIdAndTimestampGreaterThanBlock() {
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccount.getId().getAccountId(),
                        tokenAccount.getId().getTokenId(),
                        tokenAccount.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampHistoricalLessThanBlock() {
        final var tokenAccountHistory = domainBuilder.tokenAccountHistory().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory.getId().getAccountId(),
                        tokenAccountHistory.getId().getTokenId(),
                        tokenAccountHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(tokenAccountHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalEqualToBlock() {
        final var tokenAccountHistory = domainBuilder.tokenAccountHistory().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory.getId().getAccountId(),
                        tokenAccountHistory.getId().getTokenId(),
                        tokenAccountHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(tokenAccountHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalGreaterThanBlock() {
        final var tokenAccountHistory = domainBuilder.tokenAccountHistory().persist();

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory.getId().getAccountId(),
                        tokenAccountHistory.getId().getTokenId(),
                        tokenAccountHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @CsvSource(textBlock = """
            UNFROZEN, REVOKED, FROZEN, GRANTED, FROZEN, GRANTED
            UNFROZEN, REVOKED, , , UNFROZEN, REVOKED
            FROZEN, NOT_APPLICABLE, , , FROZEN, NOT_APPLICABLE
            NOT_APPLICABLE, NOT_APPLICABLE, , , NOT_APPLICABLE, NOT_APPLICABLE
            """)
    @ParameterizedTest
    void findByIdAndTimestampHistoricalReturnsLatestEntry(
            TokenFreezeStatusEnum tokenFreezeStatus,
            TokenKycStatusEnum tokenKycStatus,
            TokenFreezeStatusEnum freezeStatus,
            TokenKycStatusEnum kycStatus,
            TokenFreezeStatusEnum expectedFreezeStatus,
            TokenKycStatusEnum expectedKycStatus) {
        long accId = 2L;
        final var token = domainBuilder
                .token()
                .customize(t -> t.freezeStatus(tokenFreezeStatus).kycStatus(tokenKycStatus))
                .persist();
        final var tokenAccountHistory1 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(token.getTokenId())
                        .accountId(accId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var tokenAccountHistory2 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(token.getTokenId())
                        .accountId(accId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var latestTimestamp =
                Math.max(tokenAccountHistory1.getTimestampLower(), tokenAccountHistory2.getTimestampLower());

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory1.getId().getAccountId(),
                        tokenAccountHistory1.getId().getTokenId(),
                        latestTimestamp + 1))
                .get()
                .returns(latestTimestamp, TokenAccount::getTimestampLower)
                .returns(expectedFreezeStatus, TokenAccount::getFreezeStatus)
                .returns(expectedKycStatus, TokenAccount::getKycStatus);
    }

    @CsvSource(textBlock = """
            ,
            FROZEN, GRANTED
            """)
    @ParameterizedTest
    void findByIdAndTimestampHistoricalMissingTokenReturnsLatestEntry(
            TokenFreezeStatusEnum freezeStatus, TokenKycStatusEnum kycStatus) {
        long accId = 2L;
        long tokenId = 102L;
        final var tokenAccountHistory1 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(tokenId)
                        .accountId(accId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var tokenAccountHistory2 = domainBuilder
                .tokenAccountHistory()
                .customize(t -> t.tokenId(tokenId)
                        .accountId(accId)
                        .freezeStatus(freezeStatus)
                        .kycStatus(kycStatus))
                .persist();

        final var latestTimestamp =
                Math.max(tokenAccountHistory1.getTimestampLower(), tokenAccountHistory2.getTimestampLower());

        assertThat(repository.findByIdAndTimestamp(
                        tokenAccountHistory1.getId().getAccountId(),
                        tokenAccountHistory1.getId().getTokenId(),
                        latestTimestamp + 1))
                .get()
                .returns(latestTimestamp, TokenAccount::getTimestampLower)
                .returns(freezeStatus, TokenAccount::getFreezeStatus)
                .returns(kycStatus, TokenAccount::getKycStatus);
    }

    @Test
    void countByAccountIdAndTimestampLessThanBlock() {
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getAccountId(), tokenAccount.getTimestampLower() + 1))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 1));
    }

    @Test
    void countByAccountIdAndTimestampLessThanBlockSize() {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId).balance(0))
                .persist();
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId).balance(0))
                .persist();
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getId().getAccountId(), tokenAccount.getTimestampLower() + 1))
                .hasSize(2);
    }

    @Test
    void countByAccountIdAndTimestampEqualToBlock() {
        final var tokenAccount =
                domainBuilder.tokenAccount().customize(ta -> ta.balance(0)).persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getAccountId(), tokenAccount.getTimestampLower()))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(false, 1));
    }

    @Test
    void countByAccountIdAndTimestampGreaterThanBlock() {
        final var tokenAccount = domainBuilder.tokenAccount().persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccount.getId().getAccountId(), tokenAccount.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void countByAccountIdAndTimestampHistoricalLessThanBlock() {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, tokenAccountHistory.getTimestampLower() + 1))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 2));
    }

    @Test
    void countByAccountIdAndTimestampHistoricalEqualToBlock() {
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accountId, tokenAccountHistory.getTimestampLower()))
                .hasSize(1)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 2));
    }

    @Test
    void countByAccountIdAndTimestampHistoricalGreaterThanBlock() {
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        tokenAccountHistory.getId().getAccountId(), tokenAccountHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void countByAccountIdAndTimestampHistoricalReturnsLatestEntry() {
        long accId = 2L;
        domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accId).balance(0))
                .persist();
        domainBuilder.tokenAccountHistory().customize(ta -> ta.accountId(accId)).persist();
        final var tokenAccountHistory = domainBuilder
                .tokenAccountHistory()
                .customize(ta -> ta.accountId(accId))
                .persist();

        assertThat(repository.countByAccountIdAndTimestampAndAssociatedGroupedByBalanceIsPositive(
                        accId, tokenAccountHistory.getTimestampLower() + 1))
                .hasSize(2)
                .extracting(
                        TokenAccountAssociationsCount::getIsPositiveBalance,
                        TokenAccountAssociationsCount::getTokenCount)
                .containsExactlyInAnyOrder(tuple(true, 2), tuple(false, 1));
    }
}
