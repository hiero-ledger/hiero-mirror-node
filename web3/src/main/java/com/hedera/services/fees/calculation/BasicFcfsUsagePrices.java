// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calculation;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

import com.hedera.services.fees.pricing.RequiredPriceTypes;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.evm.pricing.RatesAndFeesLoader;

/**
 * Temporary extracted class from services.
 * </p>
 * Loads the required fee schedules from the Hedera "file system".
 *
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Remove unused methods: loadPriceSchedules, activePricingSequence
 *  2. Use RatesAndFeesLoader for the calculations
 */
@SuppressWarnings("deprecation")
public class BasicFcfsUsagePrices implements UsagePricesProvider {
    private static final Logger log = LogManager.getLogger(BasicFcfsUsagePrices.class);

    private static final long DEFAULT_FEE = 100_000L;

    private static final FeeComponents DEFAULT_PROVIDER_RESOURCE_PRICES = FeeComponents.newBuilder()
            .setMin(DEFAULT_FEE)
            .setMax(DEFAULT_FEE)
            .setConstant(0)
            .setBpt(0)
            .setVpt(0)
            .setRbh(0)
            .setSbh(0)
            .setGas(0)
            .setTv(0)
            .setBpr(0)
            .setSbpr(0)
            .build();
    public static final Map<SubType, FeeData> DEFAULT_RESOURCE_PRICES = Map.of(
            DEFAULT,
            FeeData.newBuilder()
                    .setNetworkdata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                    .setNodedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                    .setServicedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                    .build());

    private final RatesAndFeesLoader ratesAndFeesLoader;

    public BasicFcfsUsagePrices(final RatesAndFeesLoader ratesAndFeesLoader) {
        this.ratesAndFeesLoader = ratesAndFeesLoader;
    }

    @Override
    public FeeData defaultPricesGiven(final HederaFunctionality function, final Timestamp at) {
        final var feeSchedules = ratesAndFeesLoader.loadFeeSchedules(DomainUtils.timestampInNanosMax(at));
        return pricesGiven(function, at, feeSchedules).get(DEFAULT);
    }

    @Override
    public Map<SubType, FeeData> activePrices(final TxnAccessor accessor) {
        try {
            final var transactionValidStart = accessor.getTxnId().getTransactionValidStart();
            final var at = transactionValidStart.getSeconds() > 0
                    ? transactionValidStart
                    : Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .setNanos(Instant.now().getNano())
                            .build();
            return pricesGiven(
                    accessor.getFunction(),
                    at,
                    ratesAndFeesLoader.loadFeeSchedules(DomainUtils.timestampInNanosMax(at)));
        } catch (final Exception e) {
            log.warn("Using default usage prices to calculate fees for {}!", accessor.getSignedTxnWrapper(), e);
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    @Override
    public Map<SubType, FeeData> pricesGiven(
            final HederaFunctionality function, final Timestamp at, final CurrentAndNextFeeSchedule feeSchedules) {
        try {
            final Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices =
                    applicableUsagePrices(at, feeSchedules);
            final Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
            Objects.requireNonNull(usagePrices);
            return usagePrices;
        } catch (final Exception e) {
            log.debug(
                    "Default usage price will be used, no specific usage prices available for" + " function {} @ {}!",
                    function,
                    Instant.ofEpochSecond(at.getSeconds(), at.getNanos()));
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(
            final Timestamp at, final CurrentAndNextFeeSchedule feeSchedules) {
        final var applicableSchedule = onlyNextScheduleApplies(at, feeSchedules)
                ? feeSchedules.getNextFeeSchedule()
                : feeSchedules.getCurrentFeeSchedule();

        return functionUsagePricesFrom(applicableSchedule);
    }

    private boolean onlyNextScheduleApplies(final Timestamp at, final CurrentAndNextFeeSchedule feeSchedules) {
        final var currFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getCurrentFeeSchedule().getExpiryTime());
        final var nextFunctionUsagePricesExpiry =
                asTimestamp(feeSchedules.getNextFeeSchedule().getExpiryTime());

        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds()
                && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }

    private Timestamp asTimestamp(final TimestampSeconds ts) {
        return Timestamp.newBuilder().setSeconds(ts.getSeconds()).build();
    }

    private EnumMap<HederaFunctionality, Map<SubType, FeeData>> functionUsagePricesFrom(final FeeSchedule feeSchedule) {
        final EnumMap<HederaFunctionality, Map<SubType, FeeData>> allPrices = new EnumMap<>(HederaFunctionality.class);
        for (final var pricingData : feeSchedule.getTransactionFeeScheduleList()) {
            final var function = pricingData.getHederaFunctionality();
            Map<SubType, FeeData> pricesMap = allPrices.get(function);
            if (pricesMap == null) {
                pricesMap = new EnumMap<>(SubType.class);
            }
            final Set<SubType> requiredTypes = RequiredPriceTypes.requiredTypesFor(function);
            ensurePricesMapHasRequiredTypes(pricingData, pricesMap, requiredTypes);
            allPrices.put(pricingData.getHederaFunctionality(), pricesMap);
        }
        return allPrices;
    }

    private void ensurePricesMapHasRequiredTypes(
            final TransactionFeeSchedule tfs, final Map<SubType, FeeData> pricesMap, final Set<SubType> requiredTypes) {
        /* The deprecated prices are the final fallback; if even they are not set, the function will be free */
        final var oldDefaultPrices = tfs.getFeeData();
        FeeData newDefaultPrices = null;
        for (final var typedPrices : tfs.getFeesList()) {
            final var type = typedPrices.getSubType();
            if (requiredTypes.contains(type)) {
                pricesMap.put(type, typedPrices);
            }
            if (type == DEFAULT) {
                newDefaultPrices = typedPrices;
            }
        }
        for (final var type : requiredTypes) {
            if (!pricesMap.containsKey(type)) {
                if (newDefaultPrices != null) {
                    pricesMap.put(
                            type, newDefaultPrices.toBuilder().setSubType(type).build());
                } else {
                    pricesMap.put(
                            type, oldDefaultPrices.toBuilder().setSubType(type).build());
                }
            }
        }
    }
}
