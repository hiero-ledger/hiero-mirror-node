// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation.utils;

import static com.hedera.services.fees.calculation.UsageBasedFeeCalculator.numSimpleKeys;

import com.hedera.services.fees.calc.OverflowCheckingCalc;
import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.utils.fees.FeeObject;
import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;

public class PricedUsageCalculator {
    private final UsageAccumulator handleScopedAccumulator = new UsageAccumulator();

    private final AccessorBasedUsages accessorBasedUsages;
    private final OverflowCheckingCalc calculator;

    public PricedUsageCalculator(final AccessorBasedUsages accessorBasedUsages, final OverflowCheckingCalc calculator) {
        this.accessorBasedUsages = accessorBasedUsages;
        this.calculator = calculator;
    }

    public boolean supports(final HederaFunctionality function) {
        return accessorBasedUsages.supports(function);
    }

    public FeeObject inHandleFees(
            final TxnAccessor accessor, final FeeData resourcePrices, final ExchangeRate rate, final JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, handleScopedAccumulator);
    }

    public FeeObject extraHandleFees(
            final TxnAccessor accessor, final FeeData resourcePrices, final ExchangeRate rate, final JKey payerKey) {
        return fees(accessor, resourcePrices, rate, payerKey, new UsageAccumulator());
    }

    private FeeObject fees(
            final TxnAccessor accessor,
            final FeeData resourcePrices,
            final ExchangeRate rate,
            final JKey payerKey,
            final UsageAccumulator accumulator) {

        final var sigUsage = accessor.usageGiven(numSimpleKeys(payerKey));
        accessorBasedUsages.assess(sigUsage, accessor, accumulator);
        // We won't take into account congestion pricing that is used in consensus nodes,
        // since we would only simulate transactions and can't replicate the current load of the consensus network,
        // thus we can't calculate a proper multiplier.
        return calculator.fees(accumulator, resourcePrices, rate, 1L);
    }

    UsageAccumulator getHandleScopedAccumulator() {
        return handleScopedAccumulator;
    }
}
