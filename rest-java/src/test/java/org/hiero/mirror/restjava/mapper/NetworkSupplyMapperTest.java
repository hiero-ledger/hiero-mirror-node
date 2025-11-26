// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.hiero.mirror.common.domain.DomainBuilder;
import org.hiero.mirror.rest.model.NetworkSupplyResponse;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class NetworkSupplyMapperTest {

    private final DomainBuilder domainBuilder = new DomainBuilder();
    private final CommonMapper commonMapper = new CommonMapperImpl();
    private final NetworkSupplyMapper mapper = new NetworkSupplyMapperImpl();

    @Test
    void map() {
        // given
        final var consensusTimestamp = domainBuilder.timestamp();
        final var networkSupply =
                new NetworkSupply(1593057374165809903L, consensusTimestamp, NetworkSupply.TOTAL_SUPPLY);

        // when
        final var response = mapper.map(networkSupply);

        // then
        assertThat(response)
                .returns(String.valueOf(networkSupply.releasedSupply()), NetworkSupplyResponse::getReleasedSupply)
                .returns(String.valueOf(networkSupply.totalSupply()), NetworkSupplyResponse::getTotalSupply)
                .returns(commonMapper.mapTimestamp(consensusTimestamp), NetworkSupplyResponse::getTimestamp);
    }

    @Test
    void mapNull() {
        // when
        final var response = mapper.map(null);

        // then
        assertThat(response).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "100000000, 1.00000000", // 1 HBAR
        "150000000, 1.50000000", // 1.5 HBAR
        "50000000, 0.50000000", // 0.5 HBAR
        "1, 0.00000001", // 0.00000001 HBAR
        "0, 0.00000000", // 0 HBAR
        "5000000000000000000, 50000000000.00000000", // 50 billion HBAR (TOTAL_SUPPLY)
        "250000000, 2.50000000" // 2.5 HBAR
    })
    void convertToCurrencyFormat(long valueInTinyCoins, String expected) {
        // when
        final var result = mapper.convertToCurrencyFormat(valueInTinyCoins);

        // then
        assertThat(result).isEqualTo(expected);
    }
}
