// SPDX-License-Identifier: Apache-2.0

package com.hedera.node.app.fees;

public final class ExchangeRateManagerTestSupport {

    private ExchangeRateManagerTestSupport() {}

    public static void clearHistoricalRates() {
        ExchangeRateManager.HISTORICAL_RATES.invalidateAll();
    }
}
