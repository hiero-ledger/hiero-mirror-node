// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.contracts.gascalculator;

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import jakarta.inject.Named;
import org.apache.tuweni.bytes.Bytes;

/**
 * Temporary extracted class from services.
 * </p>
 * Updates gas costs enabled by gas-per-second throttling.
 */
@Named
@SuppressWarnings("java:S110")
public class GasCalculatorHederaV22 extends GasCalculatorHederaV19 {

    private static final long TX_DATA_ZERO_COST = 4L;
    private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
    private static final long TX_BASE_COST = 21_000L;

    public GasCalculatorHederaV22(final UsagePricesProvider usagePrices, final HbarCentExchange exchange) {
        super(usagePrices, exchange);
    }

    @Override
    public long transactionIntrinsicGasCost(
            final Bytes payload, final boolean isContractCreation, final long baselineGas) {
        int zeros = 0;
        for (int i = 0; i < payload.size(); i++) {
            if (payload.get(i) == 0) {
                ++zeros;
            }
        }
        final int nonZeros = payload.size() - zeros;

        long cost = TX_BASE_COST + TX_DATA_ZERO_COST * zeros + ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros;

        return isContractCreation ? (cost + txCreateExtraGasCost()) : cost;
    }
}
