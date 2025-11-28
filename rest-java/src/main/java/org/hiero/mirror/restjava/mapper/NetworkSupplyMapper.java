// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.NetworkSupplyResponse;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public interface NetworkSupplyMapper {

    long DECIMALS_IN_HBARS = 100_000_000L;
    int DECIMAL_DIGITS = String.valueOf(DECIMALS_IN_HBARS).length() - 1;

    default NetworkSupplyResponse map(NetworkSupply networkSupply) {
        if (networkSupply == null) {
            return null;
        }

        return new NetworkSupplyResponse()
                .releasedSupply(String.valueOf(networkSupply.releasedSupply()))
                .timestamp(DomainUtils.toTimestamp(networkSupply.consensusTimestamp()))
                .totalSupply(String.valueOf(NetworkSupply.TOTAL_SUPPLY));
    }

    default String convertToCurrencyFormat(long valueInTinyCoins) {
        final var valueStr = String.valueOf(valueInTinyCoins);
        // Emulate floating point division via adding leading zeroes or substring/slice
        if (valueStr.length() <= DECIMAL_DIGITS) {
            return "0." + String.format("%0" + DECIMAL_DIGITS + "d", valueInTinyCoins);
        }
        return valueStr.substring(0, valueStr.length() - DECIMAL_DIGITS)
                + "."
                + valueStr.substring(valueStr.length() - DECIMAL_DIGITS);
    }
}
