// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.restjava.RestJavaIntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
final class NetworkServiceTest extends RestJavaIntegrationTest {

    private final NetworkService networkService;

    @Test
    void returnsLatestStake() {
        // given
        final var expected = domainBuilder.networkStake().persist();

        // when
        final var actual = networkService.getLatestNetworkStake();

        // then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void throwsIfNoStakePresent() {
        assertThatThrownBy(networkService::getLatestNetworkStake)
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("No network stake data found");
    }

    @Test
    void getSupplyFromEntity() {
        // given
        final var balance = 1_000_000_000L;
        final var timestamp = domainBuilder.timestamp();
        domainBuilder
                .entity()
                .customize(e -> e.id(domainBuilder.entityNum(2).getId())
                        .balance(balance)
                        .balanceTimestamp(timestamp))
                .persist();

        // when
        final var result = networkService.getSupply(Bound.EMPTY);

        // then
        assertThat(result).isNotNull();
        assertThat(result.consensusTimestamp()).isEqualTo(timestamp);
        assertThat(result.releasedSupply()).isNotNull();
    }

    @Test
    void getSupplyNotFound() {
        // when, then
        assertThatThrownBy(() -> networkService.getSupply(Bound.EMPTY))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Network supply not found");
    }
}
