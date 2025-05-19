// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation;

import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Map;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Remove unused methods: loadPriceSchedules, activePricingSequence
 */
public interface UsagePricesProvider {
    /**
     * Returns the prices in tinyCents that are likely to be required to consume various resources while processing the
     * given operation at the given time. (In principle, the price schedules could change in the interim.)
     *
     * @param function          the operation of interest
     * @param at                the expected consensus time for the operation
     * @param feeSchedules      current and next fee schedules
     * @return the estimated prices
     */
    Map<SubType, FeeData> pricesGiven(
            HederaFunctionality function, Timestamp at, CurrentAndNextFeeSchedule feeSchedules);

    /**
     * Returns the prices in a map SubType keys and FeeData values in 1/1000th of a tinyCent that
     * must be paid to consume various resources while processing the active transaction.
     *
     * @param accessor the active transaction
     * @return the prices for the active transaction
     */
    Map<SubType, FeeData> activePrices(TxnAccessor accessor);

    /**
     * Returns the prices in tinyCents that are likely to be required to consume various resources while processing the
     * given operation at the given time. (In principle, the price schedules could change in the interim.)
     *
     * @param function the operation of interest
     * @param at       the expected consensus time for the operation
     * @return the estimated prices
     */
    FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at);
}
