// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.contracts.execution;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.getTinybarsFromTinyCents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LivePricesSourceTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567L);
    private static final Timestamp TIME_NOW = asTimestamp(NOW);
    private static final long GAS_PRICE_TINYBARS = 123;
    private static final long SBH_PRICE_TINYBARS = 456;
    private static final FeeComponents SERVICE_PRICES = FeeComponents.newBuilder()
            .setGas(GAS_PRICE_TINYBARS * 1000)
            .setSbh(SBH_PRICE_TINYBARS * 1000)
            .build();
    private static final FeeData PROVIDER_PRICES =
            FeeData.newBuilder().setServicedata(SERVICE_PRICES).build();
    private static final ExchangeRate ACTIVE_RATE =
            ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(12).build();
    private static final long REASONABLE_MULTIPLIER = 1;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private UsagePricesProvider usagePrices;

    private LivePricesSource subject;

    private static Timestamp asTimestamp(final Instant when) {
        return Timestamp.newBuilder()
                .setSeconds(when.getEpochSecond())
                .setNanos(when.getNano())
                .build();
    }

    @BeforeEach
    void setUp() {
        subject = new LivePricesSource(exchange, usagePrices);
    }

    @Test
    void getsExpectedGasPriceWithReasonableMultiplier() {
        givenCollabsWithMultiplier();

        final var expected = getTinybarsFromTinyCents(ACTIVE_RATE, GAS_PRICE_TINYBARS) * REASONABLE_MULTIPLIER;

        assertEquals(expected, subject.currentGasPrice(NOW, ContractCall));
    }

    private void givenCollabsWithMultiplier() {
        given(exchange.rate(TIME_NOW)).willReturn(ACTIVE_RATE);
        given(usagePrices.defaultPricesGiven(ContractCall, TIME_NOW)).willReturn(PROVIDER_PRICES);
    }
}
