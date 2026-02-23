// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state.singleton;

import static com.hedera.node.app.fees.schemas.V0490FeeSchema.MIDNIGHT_RATES_STATE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.state.Utils.toFileID;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.config.data.BootstrapConfig;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.transaction.RecordFile;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.hiero.mirror.web3.common.ContractCallContext;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.service.model.CallServiceParameters;
import org.hiero.mirror.web3.state.SystemFileLoader;
import org.hiero.mirror.web3.viewmodel.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class MidnightRatesSingletonTest extends Web3IntegrationTest {

    private static final long NANOS_PER_HOUR = 3600L * 1_000_000_000L;

    private final MidnightRatesSingleton midnightRatesSingleton;
    private final EvmProperties evmProperties;
    private final SystemEntity systemEntity;
    private FileID exchangeRateFileId;

    @BeforeEach
    void setUp() {
        exchangeRateFileId = toFileID(systemEntity.exchangeRateFile());
    }

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
    void getExchangeRateWithTimestampRoundsDown() {
        final SystemFileLoader systemFileLoader = mock(SystemFileLoader.class);
        final var midnightRatesSingleton = new MidnightRatesSingleton(evmProperties, systemFileLoader, systemEntity);

        final var exchangeRate = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .hbarEquiv(1)
                        .centEquiv(2)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(1))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .hbarEquiv(3)
                        .centEquiv(4)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(2))
                        .build())
                .build();

        when(systemFileLoader.load(eq(exchangeRateFileId), eq(0L))).thenReturn(fileWithRates(exchangeRate));

        long consensusTimestamp = 100;
        final CallServiceParameters params = mock(CallServiceParameters.class);
        when(params.getBlock()).thenReturn(BlockType.of("123"));
        final ExchangeRateSet result = ContractCallContext.run(ctx -> {
            ctx.setCallServiceParameters(params);
            ctx.setTimestamp(Optional.of(consensusTimestamp));
            ctx.setBlockSupplier(
                    () -> RecordFile.builder().consensusEnd(consensusTimestamp).build());
            return midnightRatesSingleton.get();
        });

        assertThat(result).isEqualTo(exchangeRate);
    }

    @Test
    void getExchangeRateWithTimestampRoundsDownToCorrectExchangeRate() {
        final SystemFileLoader systemFileLoader = mock(SystemFileLoader.class);
        final var midnightRatesSingleton = new MidnightRatesSingleton(evmProperties, systemFileLoader, systemEntity);

        final var exchangeRate = ExchangeRateSet.newBuilder()
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

        final var exchangeRate2 = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .hbarEquiv(3)
                        .centEquiv(4)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(2L))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .hbarEquiv(5)
                        .centEquiv(6)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(3L))
                        .build())
                .build();

        when(systemFileLoader.load(eq(exchangeRateFileId), eq(0L))).thenReturn(fileWithRates(exchangeRate));
        when(systemFileLoader.load(eq(exchangeRateFileId), eq(NANOS_PER_HOUR)))
                .thenReturn(fileWithRates(exchangeRate2));

        long consensusTimestamp = NANOS_PER_HOUR + 10;
        final CallServiceParameters params = mock(CallServiceParameters.class);
        when(params.getBlock()).thenReturn(BlockType.of("123"));
        final ExchangeRateSet result = ContractCallContext.run(ctx -> {
            ctx.setCallServiceParameters(params);
            ctx.setTimestamp(Optional.of(consensusTimestamp));
            ctx.setBlockSupplier(
                    () -> RecordFile.builder().consensusEnd(consensusTimestamp).build());
            return midnightRatesSingleton.get();
        });

        assertThat(result).isEqualTo(exchangeRate2);
    }

    @Test
    void getExchangeRateWithLatestBlock() {
        final SystemFileLoader systemFileLoader = mock(SystemFileLoader.class);
        final var midnightRatesSingleton = new MidnightRatesSingleton(evmProperties, systemFileLoader, systemEntity);

        final var exchangeRate = ExchangeRateSet.newBuilder()
                .currentRate(ExchangeRate.newBuilder()
                        .hbarEquiv(1)
                        .centEquiv(2)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(1))
                        .build())
                .nextRate(ExchangeRate.newBuilder()
                        .hbarEquiv(3)
                        .centEquiv(4)
                        .expirationTime(TimestampSeconds.newBuilder().seconds(2))
                        .build())
                .build();

        // We use getCurrentTimestamp() in this case so we can't pass a more specific value
        when(systemFileLoader.load(eq(exchangeRateFileId), anyLong())).thenReturn(fileWithRates(exchangeRate));

        long consensusTimestamp = 100;
        final CallServiceParameters params = mock(CallServiceParameters.class);
        when(params.getBlock()).thenReturn(BlockType.LATEST);
        final ExchangeRateSet result = ContractCallContext.run(ctx -> {
            ctx.setCallServiceParameters(params);
            ctx.setBlockSupplier(
                    () -> RecordFile.builder().consensusEnd(consensusTimestamp).build());
            return midnightRatesSingleton.get();
        });

        assertThat(result).isEqualTo(exchangeRate);
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
