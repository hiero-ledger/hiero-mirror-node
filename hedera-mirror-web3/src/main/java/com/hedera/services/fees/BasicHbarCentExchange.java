// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees;

import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import jakarta.inject.Named;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Remove unused methods: activeRates, updateRates, fcActiveRates
 *  2. Use RatesAndFeesLoader for the calculations
 */
@Named
@RequiredArgsConstructor
public final class BasicHbarCentExchange implements HbarCentExchange {
    private final RatesAndFeesLoader ratesAndFeesLoader;

    @Override
    public ExchangeRate activeRate(final Instant now) {
        final var timestamp =
                Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
        final var exchangeRates = ratesAndFeesLoader.loadExchangeRates(DomainUtils.timestampInNanosMax(timestamp));
        return rateAt(now.getEpochSecond(), exchangeRates);
    }

    @Override
    public ExchangeRate rate(final Timestamp now) {
        final var exchangeRates = ratesAndFeesLoader.loadExchangeRates(DomainUtils.timestampInNanosMax(now));
        return rateAt(now.getSeconds(), exchangeRates);
    }

    private ExchangeRate rateAt(final long now, final ExchangeRateSet exchangeRates) {
        final var currentRate = exchangeRates.getCurrentRate();
        final var currentExpiry = currentRate.getExpirationTime().getSeconds();
        return (now < currentExpiry) ? currentRate : exchangeRates.getNextRate();
    }
}
