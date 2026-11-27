// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CustomFeeRepositoryTest extends Web3IntegrationTest {

    private final CustomFeeRepository customFeeRepository;

    @Test
    void findByTokenId() {
        final var customFee = domainBuilder.customFee().persist();

        assertThat(customFeeRepository.findById(customFee.getEntityId())).contains(customFee);
    }

    @Test
    void findByIdAndTimestampLessThenBlock() {
        final var customFee = domainBuilder.customFee().persist();

        assertThat(customFeeRepository.findByTokenIdAndTimestamp(
                        customFee.getEntityId(), customFee.getTimestampLower() + 1))
                .contains(customFee);
    }

    @Test
    void findByIdAndTimestampEqualToBlock() {
        final var customFee = domainBuilder.customFee().persist();

        assertThat(customFeeRepository.findByTokenIdAndTimestamp(
                        customFee.getEntityId(), customFee.getTimestampLower()))
                .contains(customFee);
    }

    @Test
    void findByIdAndTimestampGreaterThanBlock() {
        final var customFee = domainBuilder.customFee().persist();

        assertThat(customFeeRepository.findByTokenIdAndTimestamp(
                        customFee.getEntityId(), customFee.getTimestampLower() - 1))
                .isEmpty();
    }

    @Test
    void findByIdAndTimestampHistoricalLessThenBlock() {
        final var customFeeHistory = domainBuilder.customFeeHistory().persist();

        assertThat(customFeeRepository.findByTokenIdAndTimestamp(
                        customFeeHistory.getEntityId(), customFeeHistory.getTimestampLower() + 1))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(customFeeHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalEqualToBlock() {
        final var customFeeHistory = domainBuilder.customFeeHistory().persist();

        assertThat(customFeeRepository.findByTokenIdAndTimestamp(
                        customFeeHistory.getEntityId(), customFeeHistory.getTimestampLower()))
                .get()
                .usingRecursiveComparison()
                .isEqualTo(customFeeHistory);
    }

    @Test
    void findByIdAndTimestampHistoricalGreaterThanBlock() {
        final var customFeeHistory = domainBuilder.customFeeHistory().persist();

        assertThat(customFeeRepository.findByTokenIdAndTimestamp(
                        customFeeHistory.getEntityId(), customFeeHistory.getTimestampLower() - 1))
                .isEmpty();
    }
}
