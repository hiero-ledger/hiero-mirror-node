// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees.calc;

import static com.hedera.services.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.HRS_DIVISOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.utils.fees.FeeBuilder;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import org.junit.jupiter.api.Test;

class OverflowCheckingCalcTest {
    private static final int RATE_TINYBAR_COMPONENT = 1001;
    private static final int RATE_TINYCENT_COMPONENT = 1000;
    private static final ExchangeRate SOME_RATE = ExchangeRate.newBuilder()
            .setHbarEquiv(RATE_TINYBAR_COMPONENT)
            .setCentEquiv(RATE_TINYCENT_COMPONENT)
            .build();
    private static final OverflowCheckingCalc SUBJECT = new OverflowCheckingCalc();

    @Test
    void throwsOnMultiplierOverflow() {
        final var usage = new UsageAccumulator();
        copyData(MOCK_USAGE, usage);

        assertThrows(IllegalArgumentException.class, () -> SUBJECT.fees(usage, mockPrices, mockRate, Long.MAX_VALUE));
    }

    @Test
    void converterCanFallbackToBigDecimal() {
        final var highFee = Long.MAX_VALUE / RATE_TINYCENT_COMPONENT;
        final var expectedTinybarFee = FeeBuilder.getTinybarsFromTinyCents(SOME_RATE, highFee);

        final long computedTinybarFee = OverflowCheckingCalc.tinycentsToTinybars(highFee, SOME_RATE);

        assertEquals(expectedTinybarFee, computedTinybarFee);
    }

    @Test
    void safeAccumulateTwoWorks() {
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateTwo(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateTwo(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateTwo(1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateTwo(1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateTwo(1, 1, Long.MAX_VALUE));

        assertEquals(3, SUBJECT.safeAccumulateTwo(1, 1, 1));
    }

    @Test
    void safeAccumulateThreeWorks() {
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateThree(-1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateThree(1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateThree(1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateThree(1, 1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateThree(1, Long.MAX_VALUE, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateThree(1, 1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateThree(1, 1, 1, Long.MAX_VALUE));

        assertEquals(4, SUBJECT.safeAccumulateThree(1, 1, 1, 1));
    }

    @Test
    void safeAccumulateFourWorks() {
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(-1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, -1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, 1, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, 1, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, 1, 1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, Long.MAX_VALUE, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, 1, Long.MAX_VALUE, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, 1, 1, Long.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> SUBJECT.safeAccumulateFour(1, 1, 1, 1, Long.MAX_VALUE));

        assertEquals(5, SUBJECT.safeAccumulateFour(1, 1, 1, 1, 1));
    }

    private static final FeeComponents mockFees = FeeComponents.newBuilder()
            .setMax(Long.MAX_VALUE)
            .setConstant(1_234_567L)
            .setBpr(1_000_000L)
            .setBpt(2_000_000L)
            .setRbh(3_000_000L)
            .setSbh(4_000_000L)
            .build();
    private static final ExchangeRate mockRate =
            ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(120).build();

    private static final FeeData mockPrices = FeeData.newBuilder()
            .setNetworkdata(mockFees)
            .setNodedata(mockFees)
            .setServicedata(mockFees)
            .build();

    private static final long ONE = 1;
    private static final long BPT = 2;
    private static final long VPT = 3;
    private static final long RBH = 4;
    private static final long SBH = 5;
    private static final long BPR = 8;
    private static final long SBPR = 9;
    private static final long NETWORK_RBH = 10;
    private static final FeeComponents MOCK_USAGE_VECTOR = FeeComponents.newBuilder()
            .setConstant(ONE)
            .setBpt(BPT)
            .setVpt(VPT)
            .setRbh(RBH)
            .setSbh(SBH)
            .setBpr(BPR)
            .setSbpr(SBPR)
            .build();
    private static final FeeData MOCK_USAGE =
            ESTIMATOR_UTILS.withDefaultTxnPartitioning(MOCK_USAGE_VECTOR, SubType.DEFAULT, NETWORK_RBH, 3);

    private static final void copyData(final FeeData feeData, final UsageAccumulator into) {
        into.setNumPayerKeys(feeData.getNodedata().getVpt());
        into.addVpt(feeData.getNetworkdata().getVpt());
        into.addBpt(feeData.getNetworkdata().getBpt());
        into.addBpr(feeData.getNodedata().getBpr());
        into.addSbpr(feeData.getNodedata().getSbpr());
        into.addNetworkRbs(feeData.getNetworkdata().getRbh() * HRS_DIVISOR);
        into.addRbs(feeData.getServicedata().getRbh() * HRS_DIVISOR);
        into.addSbs(feeData.getServicedata().getSbh() * HRS_DIVISOR);
    }
}
