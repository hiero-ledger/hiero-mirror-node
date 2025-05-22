// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.TokenAllowance;
import org.hiero.mirror.common.domain.token.TokenTransfer;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAllowanceRepositoryTest extends Web3IntegrationTest {
    private final TokenAllowanceRepository repository;

    @Test
    void findAllowance() {
        final var tokenAllowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository
                        .findById(tokenAllowance.getId())
                        .map(TokenAllowance::getAmount)
                        .orElse(0L))
                .isEqualTo(tokenAllowance.getAmount());
    }

    @Test
    void findByOwner() {
        long owner = 22L;
        final var tokenAllowance =
                domainBuilder.tokenAllowance().customize(a -> a.owner(owner)).persist();

        assertThat(repository.findByOwner(owner)).hasSize(1).contains(tokenAllowance);
    }

    @Test
    void findByIdAndTimestampGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByOwnerAndTimestampLessThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowance.getOwner(), allowance.getTimestampLower() + 1)
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByOwnerAndTimestampEqualToBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowance.getOwner(), allowance.getTimestampLower())
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByOwnerAndTimestampGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerAndTimestamp(allowance.getOwner(), allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByOwnerAndTimestampHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowanceHistory.getOwner(), allowanceHistory.getTimestampLower() + 1)
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByOwnerAndTimestampHistoricalEqualToBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowanceHistory.getOwner(), allowanceHistory.getTimestampLower())
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByOwnerAndTimestampHistoricalGreaterThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerAndTimestamp(
                        allowanceHistory.getOwner(), allowanceHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByOwnerAndTimestampHistoricalReturnsLatestEntry() {
        long owner = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        Range<Long> historyTimestampRange1 = Range.closedOpen(1L, 10L);
        Range<Long> historyTimestampRange2 = Range.closedOpen(10L, 20L);

        final var allowanceHistory1 = domainBuilder
                .tokenAllowanceHistory()
                .customize(
                        a -> a.owner(owner).tokenId(tokenId).spender(spenderId).timestampRange(historyTimestampRange1))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .tokenAllowanceHistory()
                .customize(
                        a -> a.owner(owner).tokenId(tokenId).spender(spenderId).timestampRange(historyTimestampRange2))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        assertThat(repository
                        .findByOwnerAndTimestamp(owner, latestTimestamp + 1)
                        .get(0))
                .returns(latestTimestamp, TokenAllowance::getTimestampLower);
    }

    @Test
    void findByOwnerAndTimestampWithTransferHappyPath() {
        long ownerId = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp1 = tokenAllowanceTimestamp + 2;
        long blockTimestamp = tokenAllowanceTimestamp + 3;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // allowance history
        domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId + 1)
                        .timestampRange(Range.open(tokenAllowanceTimestamp, tokenAllowanceTimestamp + 1000)))
                .persist();

        // token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        // another token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp1)
                                .build()))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).returns(initialAmount + 2 * amountForTransfer, TokenAllowance::getAmount);
        assertThat(result.get(1)).returns(initialAmount, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithHistoryTransferOverlaps() {
        var ownerId = 1L;
        var tokenId = 2L;
        var spenderId = 3L;

        var tokenAllowanceTimestamp = System.currentTimeMillis();
        var tokenTransferTimestamp = tokenAllowanceTimestamp + 1;
        var historyRange = Range.openClosed(tokenAllowanceTimestamp - 1000000, tokenAllowanceTimestamp - 1);
        var blockTimestamp = tokenAllowanceTimestamp + 3;

        var initialAmount = 3L;
        var initialHistoryAmount = 10L;
        var amountForTransfer = -1L;
        var amountForHistoryTransfer = -2L;
        var amountForContractCallTransfer = -5L;

        var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // another token allowance
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId + 1)
                        .timestampRange(Range.atLeast(historyRange.lowerEndpoint())))
                .persist();

        // token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        var historyTokenTransfer = domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForHistoryTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(historyRange.lowerEndpoint() + 2)
                                .build()))
                .persist();

        // allowance history
        domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialHistoryAmount)
                        .amount(initialHistoryAmount + historyTokenTransfer.getAmount())
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(historyRange))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId + 1))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(historyRange.lowerEndpoint() + 1)
                                .build()))
                .persist();

        var contractCallTransfer = domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForContractCallTransfer)
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(historyRange.lowerEndpoint() + 3)
                                .build()))
                .persist();

        // contract result
        domainBuilder
                .contractResult()
                .customize(
                        c -> c.consensusTimestamp(contractCallTransfer.getId().getConsensusTimestamp())
                                .senderId(EntityId.of(spenderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
        assertThat(result.get(1)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);

        result = repository.findByOwnerAndTimestamp(
                allowance.getOwner(), historyTokenTransfer.getId().getConsensusTimestamp());

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).returns(initialHistoryAmount + amountForHistoryTransfer, TokenAllowance::getAmount);
        assertThat(result.get(1)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);

        result = repository.findByOwnerAndTimestamp(
                allowance.getOwner(), contractCallTransfer.getId().getConsensusTimestamp());

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
                .returns(
                        initialHistoryAmount + amountForHistoryTransfer + amountForContractCallTransfer,
                        TokenAllowance::getAmount);
        assertThat(result.get(1)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithTransferMultipleEntries() {
        long ownerId = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp1 = tokenAllowanceTimestamp + 2;
        long blockTimestamp = tokenAllowanceTimestamp + 3;
        final long initialAmount = 3;
        final long amountForTransfer = -1;
        final long amountForTransfer1 = -2;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // allowance history
        domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId + 1)
                        .timestampRange(Range.open(tokenAllowanceTimestamp, tokenAllowanceTimestamp + 1000)))
                .persist();

        // token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        // another token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer1)
                        .payerAccountId(EntityId.of(spenderId + 1))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp1)
                                .build()))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
        assertThat(result.get(1)).returns(initialAmount + amountForTransfer1, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithTransferAfterBlockTimestamp() {
        long ownerId = 1L;
        long tokenId = 2L;
        long spenderId = 3L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long blockTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 2;
        final long initialAmount = 3;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .spender(spenderId)
                        .tokenId(tokenId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // This transfer should not be selected and the amount should not be subtracted from the allowance.
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(-1)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .accountId(EntityId.of(ownerId))
                                .build()))
                .persist();

        assertThat(repository
                        .findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp)
                        .get(0))
                .returns(initialAmount, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithContractTransfer() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 1;
        long blockTimestamp = tokenAllowanceTimestamp + 2;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransferTimestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithContractTransferAfterBlockTimestamp() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long blockTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 2;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        // contract result
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransferTimestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).returns(initialAmount, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampWithRegularTokenTransfersAndContractTokenTransfers() {
        long ownerId = 100L;
        long tokenId = 200L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 100;
        long tokenTransfer1Timestamp = tokenAllowanceTimestamp + 200;
        long blockTimestamp = tokenAllowanceTimestamp + 300;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        // contract token transfer
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer1Timestamp)
                                .build()))
                .persist();

        // contract result
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer1Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).returns(initialAmount + 2 * amountForTransfer, TokenAllowance::getAmount);
    }

    @Test
    void findByOwnerAndTimestampPreventDoubleTransferDecrease() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenAllowanceTimestamp1 = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 100;
        long tokenTransfer1Timestamp = tokenTransferTimestamp + 1;
        long tokenTransfer2Timestamp = tokenTransferTimestamp + 2;
        long blockTimestamp = tokenAllowanceTimestamp + 300;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        // another token allowance
        domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp1)))
                .persist();

        // token transfer, must be decreased only for the second allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        // token transfer, must be decreased only for the first allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer1Timestamp)
                                .build()))
                .persist();

        // contract token transfer, must be decreased only for the first allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer2Timestamp)
                                .build()))
                .persist();

        // contact result
        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer2Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerAndTimestamp(allowance.getOwner(), blockTimestamp);
        assertThat(result).hasSize(2);
        assertThat(result.stream()
                        .filter(ta -> ta.getSpender() == senderId)
                        .toList()
                        .get(0))
                .returns(initialAmount + 2 * amountForTransfer, TokenAllowance::getAmount);
        assertThat(result.stream()
                        .filter(ta -> ta.getSpender() == spenderId)
                        .toList()
                        .get(0))
                .returns(initialAmount + amountForTransfer, TokenAllowance::getAmount);
    }

    @Test
    void findByIdAndTimestampEqualToBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower()))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByIdAndTimestampLessThanBlockTimestamp() {
        final var allowance = domainBuilder.tokenAllowance().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowance.getOwner(),
                        allowance.getId().getSpender(),
                        allowance.getTokenId(),
                        allowance.getTimestampLower() + 1))
                .get()
                .isEqualTo(allowance);
    }

    @Test
    void findByIdAndTimestampHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowanceHistory.getOwner(),
                        allowanceHistory.getId().getSpender(),
                        allowanceHistory.getTokenId(),
                        allowanceHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalEqualToBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowanceHistory.getOwner(),
                        allowanceHistory.getId().getSpender(),
                        allowanceHistory.getTokenId(),
                        allowanceHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalGreaterThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder.tokenAllowanceHistory().persist();

        assertThat(repository.findByOwnerSpenderTokenAndTimestamp(
                        allowanceHistory.getOwner(),
                        allowanceHistory.getId().getSpender(),
                        allowanceHistory.getTokenId(),
                        allowanceHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampHistoricalReturnsLatestEntry() {
        long tokenId = 1L;
        long owner = 2L;
        long spender = 3L;
        final var allowanceHistory1 = domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .tokenAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        TokenAllowance actualAllowance = repository
                .findByOwnerSpenderTokenAndTimestamp(
                        allowanceHistory1.getOwner(),
                        allowanceHistory1.getId().getSpender(),
                        allowanceHistory1.getTokenId(),
                        latestTimestamp + 1)
                .get();

        assertThat(actualAllowance).usingRecursiveComparison().isEqualTo(allowanceHistory2);
        assertThat(actualAllowance.getTimestampLower()).isEqualTo(latestTimestamp);
    }

    @Test
    void findByOwnerSpenderTokenAndTimestampWithContractTransferAfterBlockTimestamp() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long blockTimestamp = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 2;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransferTimestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerSpenderTokenAndTimestamp(
                allowance.getOwner(), allowance.getSpender(), allowance.getTokenId(), blockTimestamp);
        assertThat(result.get().getAmount()).isEqualTo(initialAmount);
    }

    @Test
    void findByOwnerSpenderTokenAndTimestampWithRegularTokenTransfersAndContractTokenTransfers() {
        long ownerId = 100L;
        long tokenId = 200L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 100;
        long tokenTransfer1Timestamp = tokenAllowanceTimestamp + 200;
        long blockTimestamp = tokenAllowanceTimestamp + 300;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer1Timestamp)
                                .build()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer1Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerSpenderTokenAndTimestamp(
                allowance.getOwner(), allowance.getSpender(), allowance.getTokenId(), blockTimestamp);
        assertThat(result.get().getAmount()).isEqualTo(initialAmount + 2 * amountForTransfer);
    }

    @Test
    void findByOwnerSpenderTokenAndTimestampPreventDoubleTransferDecrease() {
        long ownerId = 100L;
        long tokenId = 200L;
        long spenderId = 300L;
        long senderId = 400L;
        long tokenAllowanceTimestamp = System.currentTimeMillis();
        long tokenAllowanceTimestamp1 = tokenAllowanceTimestamp + 1;
        long tokenTransferTimestamp = tokenAllowanceTimestamp + 100;
        long tokenTransfer1Timestamp = tokenTransferTimestamp + 1;
        long tokenTransfer2Timestamp = tokenTransferTimestamp + 2;
        long blockTimestamp = tokenAllowanceTimestamp + 300;
        final long initialAmount = 3;
        final long amountForTransfer = -1;

        final var allowance = domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(senderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp)))
                .persist();

        domainBuilder
                .tokenAllowance()
                .customize(a -> a.owner(ownerId)
                        .amountGranted(initialAmount)
                        .amount(initialAmount)
                        .tokenId(tokenId)
                        .spender(spenderId)
                        .timestampRange(Range.atLeast(tokenAllowanceTimestamp1)))
                .persist();

        // Must be decreased only for the second allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(spenderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransferTimestamp)
                                .build()))
                .persist();

        // Must be decreased only for the first allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer1Timestamp)
                                .build()))
                .persist();

        // Must be decreased only for the first allowance
        domainBuilder
                .tokenTransfer()
                .customize(t -> t.isApproval(true)
                        .amount(amountForTransfer)
                        .payerAccountId(EntityId.of(senderId))
                        .id(TokenTransfer.Id.builder()
                                .tokenId(EntityId.of(tokenId))
                                .accountId(EntityId.of(ownerId))
                                .consensusTimestamp(tokenTransfer2Timestamp)
                                .build()))
                .persist();

        domainBuilder
                .contractResult()
                .customize(c -> c.consensusTimestamp(tokenTransfer2Timestamp).senderId(EntityId.of(senderId)))
                .persist();

        var result = repository.findByOwnerSpenderTokenAndTimestamp(
                allowance.getOwner(), allowance.getSpender(), allowance.getTokenId(), blockTimestamp);
        assertThat(result.get().getAmount()).isEqualTo(initialAmount + 2 * amountForTransfer);
    }
}
