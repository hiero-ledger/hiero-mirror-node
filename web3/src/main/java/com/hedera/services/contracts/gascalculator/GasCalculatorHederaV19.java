// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.contracts.gascalculator;

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import jakarta.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

/**
 * Temporary extracted class from services
 *
 * Differences with the original:
 *  1. Hardcoded storage duration property
 * </p>
 * Provides Hedera adapted gas cost lookups and calculations used during transaction processing.
 * Maps the gas costs of the Smart Contract Service including and after 0.19.0 release
 */
@SuppressWarnings("java:S110")
public class GasCalculatorHederaV19 extends LondonGasCalculator {
    private static final long LOG_STORAGE_DURATION_SEC = 180L;

    private final UsagePricesProvider usagePrices;
    private final HbarCentExchange exchange;

    @Inject
    public GasCalculatorHederaV19(final UsagePricesProvider usagePrices, final HbarCentExchange exchange) {
        this.usagePrices = usagePrices;
        this.exchange = exchange;
    }

    @Override
    public long transactionIntrinsicGasCost(
            final Bytes payload, final boolean isContractCreate, final long baselineGas) {
        return 0L;
    }

    @Override
    public long logOperationGasCost(
            final MessageFrame frame, final long dataOffset, final long dataLength, final int numTopics) {
        final var gasCost = GasCalculatorHederaUtil.logOperationGasCost(
                usagePrices, exchange, frame, LOG_STORAGE_DURATION_SEC, dataOffset, dataLength, numTopics);
        return Math.max(super.logOperationGasCost(frame, dataOffset, dataLength, numTopics), gasCost);
    }
}
