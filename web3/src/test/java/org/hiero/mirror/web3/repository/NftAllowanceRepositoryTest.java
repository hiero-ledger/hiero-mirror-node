// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.AbstractNftAllowance;
import org.hiero.mirror.common.domain.entity.NftAllowance;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@RequiredArgsConstructor
class NftAllowanceRepositoryTest extends Web3IntegrationTest {
    private final NftAllowanceRepository allowanceRepository;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void spenderHasApproveForAll(boolean isApproveForAll) {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(isApproveForAll))
                .persist();

        assertThat(allowanceRepository
                        .findById(allowance.getId())
                        .map(NftAllowance::isApprovedForAll)
                        .orElse(false))
                .isEqualTo(isApproveForAll);
    }

    @Test
    void findBySpenderAndApprovedForAllIsTrue() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(true))
                .persist();
        assertThat(allowanceRepository.findByOwnerAndApprovedForAllIsTrue(allowance.getOwner()))
                .hasSize(1)
                .contains(allowance);
    }

    @Test
    void noMatchingRecord() {
        assertThat(allowanceRepository
                        .findById(new AbstractNftAllowance.Id())
                        .map(NftAllowance::isApprovedForAll)
                        .orElse(false))
                .isFalse();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(e -> e.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowance.getId().getOwner(), allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsFalseGreaterThanBlockTimestamp() {
        final var allowance = domainBuilder.nftAllowance().persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowance.getId().getOwner(), allowance.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueEqualToBlockTimestamp() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(e -> e.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowance.getId().getOwner(), allowance.getTimestampLower())
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueLessThanBlockTimestamp() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(e -> e.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowance.getId().getOwner(), allowance.getTimestampLower() + 1)
                        .get(0))
                .isEqualTo(allowance);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsFalseLessThanBlockTimestamp() {
        final var allowance = domainBuilder.nftAllowance().persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowance.getId().getOwner(), allowance.getTimestampLower() + 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower() + 1)
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsFalseHistoricalLessThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(false))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower() + 1)
                        .isEmpty())
                .isTrue();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalEqualToBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository
                        .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                                allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower())
                        .get(0))
                .usingRecursiveComparison()
                .isEqualTo(allowanceHistory);
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalGreaterThanBlockTimestamp() {
        final var allowanceHistory = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.approvedForAll(true))
                .persist();

        assertThat(allowanceRepository.findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowanceHistory.getId().getOwner(), allowanceHistory.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByTimestampAndOwnerAndApprovedForAllIsTrueHistoricalReturnsLatestEntry() {
        long tokenId = 1L;
        long owner = 2L;
        long spender = 3L;
        final var allowanceHistory1 = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender).approvedForAll(true))
                .persist();

        final var allowanceHistory2 = domainBuilder
                .nftAllowanceHistory()
                .customize(a -> a.tokenId(tokenId).owner(owner).spender(spender).approvedForAll(true))
                .persist();

        final var latestTimestamp =
                Math.max(allowanceHistory1.getTimestampLower(), allowanceHistory2.getTimestampLower());

        NftAllowance actualAllowance = allowanceRepository
                .findByOwnerAndTimestampAndApprovedForAllIsTrue(
                        allowanceHistory1.getId().getOwner(), latestTimestamp + 1)
                .get(0);

        assertThat(actualAllowance).usingRecursiveComparison().isEqualTo(allowanceHistory2);
        assertThat(actualAllowance.getTimestampLower()).isEqualTo(latestTimestamp);
    }
}
