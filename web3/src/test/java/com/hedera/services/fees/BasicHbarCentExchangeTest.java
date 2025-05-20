// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.fees;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import org.hiero.mirror.web3.evm.pricing.RatesAndFeesLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicHbarCentExchangeTest {
    private static final long CROSSOVER_TIME = 1_234_567L;
    private static final ExchangeRateSet RATES = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setHbarEquiv(1)
                    .setCentEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(CROSSOVER_TIME)))
            .setNextRate(ExchangeRate.newBuilder()
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(CROSSOVER_TIME * 2))
                    .setHbarEquiv(1)
                    .setCentEquiv(24))
            .build();

    private BasicHbarCentExchange subject;

    @Mock
    private RatesAndFeesLoader ratesAndFeesLoader;

    @BeforeEach
    void setUp() {
        when(ratesAndFeesLoader.loadExchangeRates(anyLong())).thenReturn(RATES);

        subject = new BasicHbarCentExchange(ratesAndFeesLoader);
    }

    @Test
    void updatesRatesWhenRatesCalled() {
        subject.rate(beforeCrossTime);

        verify(ratesAndFeesLoader).loadExchangeRates(DomainUtils.timestampInNanosMax(beforeCrossTime));
    }

    @Test
    void returnsCurrentRatesWhenBeforeCrossTime() {
        final var result = subject.rate(beforeCrossTime);

        assertEquals(RATES.getCurrentRate(), result);
    }

    @Test
    void returnsNextRatesWhenAfterCrossTime() {
        final var result = subject.rate(afterCrossTime);

        assertEquals(RATES.getNextRate(), result);
    }

    private static final Timestamp beforeCrossTime =
            Timestamp.newBuilder().setSeconds(CROSSOVER_TIME - 1).build();
    private static final Timestamp afterCrossTime =
            Timestamp.newBuilder().setSeconds(CROSSOVER_TIME).build();
}
