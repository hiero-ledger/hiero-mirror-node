// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.config.data.BootstrapConfig;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class MidnightRatesSingletonTest extends Web3IntegrationTest {

    private final MidnightRatesSingleton midnightRatesSingleton;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Test
    void get() {
        final var bootstrapConfig =
                mirrorNodeEvmProperties.getVersionedConfiguration().getConfigData(BootstrapConfig.class);

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
        assertThat(midnightRatesSingleton.getStateId()).isEqualTo(MIDNIGHT_RATES_STATE_ID);
    }
}
