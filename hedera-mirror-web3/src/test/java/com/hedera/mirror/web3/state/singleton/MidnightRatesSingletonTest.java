// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.config.data.BootstrapConfig;
import org.junit.jupiter.api.Test;

class MidnightRatesSingletonTest {

    private final MidnightRatesSingleton midnightRatesSingleton = new MidnightRatesSingleton();

    @Test
    void get() {
        final var bootstrapConfig =
                new BootstrapConfigProviderImpl().getConfiguration().getConfigData(BootstrapConfig.class);

        final var expected = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .centEquiv(bootstrapConfig.ratesCurrentCentEquiv())
                        .hbarEquiv(bootstrapConfig.ratesCurrentHbarEquiv())
                        .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesCurrentExpiry()))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .centEquiv(bootstrapConfig.ratesNextCentEquiv())
                        .hbarEquiv(bootstrapConfig.ratesNextHbarEquiv())
                        .expirationTime(TimestampSeconds.newBuilder().seconds(bootstrapConfig.ratesNextExpiry()))
                        .build())
                .build();
        assertThat(midnightRatesSingleton.get()).isEqualTo(expected);
    }

    @Test
    void key() {
        assertThat(midnightRatesSingleton.getKey()).isEqualTo(MIDNIGHT_RATES_STATE_KEY);
    }
}
