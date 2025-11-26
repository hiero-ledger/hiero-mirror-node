// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.mapper;

import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.NetworkSupplyResponse;
import org.hiero.mirror.restjava.dto.NetworkSupply;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

@Mapper(config = MapperConfiguration.class)
public interface NetworkSupplyMapper {

    long DECIMALS_IN_HBARS = 100_000_000L;
    int DECIMAL_DIGITS = String.valueOf(DECIMALS_IN_HBARS).length() - 1;

    default NetworkSupplyResponse map(NetworkSupply networkSupply) {
        if (networkSupply == null) {
            return null;
        }

        return new NetworkSupplyResponse()
                .releasedSupply(networkSupply.releasedSupply())
                .timestamp(DomainUtils.toTimestamp(networkSupply.consensusTimestamp()))
                .totalSupply(networkSupply.totalSupply());
    }

    @Named("convertToCurrencyFormat")
    default String convertToCurrencyFormat(String valueInTinyCoins, String currencyFormat) {
        return switch (currencyFormat) {
            case "TINYBARS" -> valueInTinyCoins;
            case "HBARS" -> convertToHbars(valueInTinyCoins);
            default -> convertToBoth(valueInTinyCoins);
        };
    }

    @Named("convertToHbars")
    default String convertToHbars(String valueInTinyCoins) {
        // Emulate integer division via substring
        if (valueInTinyCoins.length() <= DECIMAL_DIGITS) {
            return "0";
        }
        return valueInTinyCoins.substring(0, valueInTinyCoins.length() - DECIMAL_DIGITS);
    }

    @Named("convertToBoth")
    default String convertToBoth(String valueInTinyCoins) {
        // Emulate floating point division via adding leading zeroes or substring/slice
        if (valueInTinyCoins.length() <= DECIMAL_DIGITS) {
            return "0." + String.format("%0" + DECIMAL_DIGITS + "d", Long.parseLong(valueInTinyCoins));
        }
        return valueInTinyCoins.substring(0, valueInTinyCoins.length() - DECIMAL_DIGITS)
                + "."
                + valueInTinyCoins.substring(valueInTinyCoins.length() - DECIMAL_DIGITS);
    }
}
