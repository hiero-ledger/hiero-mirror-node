// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.token.FixedFee;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CustomFeeRepositoryTest extends Web3IntegrationTest {

    private final CustomFeeRepository customFeeRepository;

    @Test
    void findByTokenId() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getEntityId();

        var result = customFeeRepository.findById(tokenId);
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampLessThenBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getEntityId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() + 1);
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampEqualToBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getEntityId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower());
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampGreaterThanBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getEntityId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() - 1);
        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndTimestampHistoricalLessThenBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFeeHistory()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getEntityId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() + 1);
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampHistoricalEqualToBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFeeHistory()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getEntityId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower());
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampHistoricalGreaterThanBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFeeHistory()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getEntityId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() - 1);
        assertThat(result).isEmpty();
    }
}
