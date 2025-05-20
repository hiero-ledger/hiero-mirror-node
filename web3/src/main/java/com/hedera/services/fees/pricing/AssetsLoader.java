// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

public class AssetsLoader {
    private static final String CANONICAL_PRICES_RESOURCE = "canonical-prices.json";
    private Map<HederaFunctionality, Map<SubType, BigDecimal>> cachedCanonicalPrices = null;

    /**
     * Loads a map that, for each supported operation, gives the desired price in USD for the "base
     * configuration" of each type of that operation. (Types are given by the values of the {@link
     * SubType} enum; that is, DEFAULT, TOKEN_NON_FUNGIBLE_UNIQUE, and TOKEN_FUNGIBLE_COMMON.)
     *
     * @return the desired per-type prices, in USD
     * @throws IOException if the backing JSON resource cannot be loaded
     */
    @SuppressWarnings("unchecked")
    public Map<HederaFunctionality, Map<SubType, BigDecimal>> loadCanonicalPrices() throws IOException {
        if (cachedCanonicalPrices != null) {
            return cachedCanonicalPrices;
        }
        try (final var fin = AssetsLoader.class.getClassLoader().getResourceAsStream(CANONICAL_PRICES_RESOURCE)) {
            final var om = new ObjectMapper();
            final var prices = om.readValue(fin, Map.class);

            final Map<HederaFunctionality, Map<SubType, BigDecimal>> typedPrices =
                    new EnumMap<>(HederaFunctionality.class);
            prices.forEach((funName, priceMap) -> {
                final var function = HederaFunctionality.valueOf((String) funName);
                final Map<SubType, BigDecimal> scopedPrices = new EnumMap<>(SubType.class);
                ((Map) priceMap).forEach((typeName, price) -> {
                    final var type = SubType.valueOf((String) typeName);
                    final var bdPrice = (price instanceof Double val)
                            ? BigDecimal.valueOf(val)
                            : BigDecimal.valueOf((Integer) price);
                    scopedPrices.put(type, bdPrice);
                });
                typedPrices.put(function, scopedPrices);
            });

            cachedCanonicalPrices = typedPrices;
            return typedPrices;
        }
    }
}
