// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.config.data.BootstrapConfig;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class MidnightRatesSingletonTest extends Web3IntegrationTest {

    private final MidnightRatesSingleton midnightRatesSingleton;
    private final EvmProperties evmProperties;
    private final SystemEntity systemEntity = new SystemEntity(CommonProperties.getInstance());

    @Test
    void get() {
        final var bootstrapConfig = evmProperties.getVersionedConfiguration().getConfigData(BootstrapConfig.class);

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

    @Test
    void getWithDifferentTimestampsReturnsDifferentCorrectRates() {
        final long timestamp1 = 100L;
        final long timestamp2 = 200L;

        final SystemFileLoader systemFileLoader = mock(SystemFileLoader.class);
        final var midnightRatesSingleton = new MidnightRatesSingleton(evmProperties, systemFileLoader);

        final var rates1 = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .hbarEquiv(1)
                        .centEquiv(2)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(1L))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .hbarEquiv(3)
                        .centEquiv(4)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(2L))
                        .build())
                .build();

        final var rates2 = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .hbarEquiv(10)
                        .centEquiv(20)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(10L))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .hbarEquiv(30)
                        .centEquiv(40)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(20L))
                        .build())
                .build();

        final var rates3 = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .hbarEquiv(100)
                        .centEquiv(200)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(100L))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .hbarEquiv(300)
                        .centEquiv(400)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(200L))
                        .build())
                .build();

        when(systemFileLoader.load(any(FileID.class), eq(timestamp1))).thenReturn(fileWithRates(rates1));
        when(systemFileLoader.load(any(FileID.class), eq(timestamp2))).thenReturn(fileWithRates(rates2));
        when(systemFileLoader.load(any(FileID.class), eq(Long.MAX_VALUE))).thenReturn(fileWithRates(rates3));

        final CallServiceParameters params1 = mock(CallServiceParameters.class);
        when(params1.getBlock()).thenReturn(BlockType.EARLIEST);
        final ExchangeRateSet result1 = ContractCallContext.run(ctx -> {
            ctx.setCallServiceParameters(params1);
            ctx.setBlockSupplier(
                    () -> RecordFile.builder().consensusEnd(timestamp1).build());
            return midnightRatesSingleton.get();
        });

        final CallServiceParameters params2 = mock(CallServiceParameters.class);
        when(params2.getBlock()).thenReturn(BlockType.of("200"));
        final ExchangeRateSet result2 = ContractCallContext.run(ctx -> {
            ctx.setCallServiceParameters(params2);
            ctx.setBlockSupplier(
                    () -> RecordFile.builder().consensusEnd(timestamp2).build());
            return midnightRatesSingleton.get();
        });

        final CallServiceParameters params3 = mock(CallServiceParameters.class);
        when(params3.getBlock()).thenReturn(BlockType.LATEST);
        final ExchangeRateSet result3 = ContractCallContext.run(ctx -> {
            ctx.setCallServiceParameters(params3);
            return midnightRatesSingleton.get();
        });

        assertThat(result1).isEqualTo(rates1);
        assertThat(result2).isEqualTo(rates2);
        assertThat(result3).isEqualTo(rates3);
        assertThat(result1).isNotEqualTo(result2);
        assertThat(result2).isNotEqualTo(result3);
        assertThat(result1).isNotEqualTo(result3);
    }

    private File fileWithRates(ExchangeRateSet exchangeRateSet) {
        final var fileId = systemEntity.exchangeRateFile();
        return File.newBuilder()
                .fileId(FileID.newBuilder()
                        .shardNum(fileId.getShard())
                        .realmNum(fileId.getRealm())
                        .fileNum(fileId.getNum())
                        .build())
                .contents(ExchangeRateSet.PROTOBUF.toBytes(exchangeRateSet))
                .build();
    }
}
