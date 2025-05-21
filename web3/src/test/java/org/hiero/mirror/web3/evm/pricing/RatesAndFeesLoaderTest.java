// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.evm.pricing;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.Optional;
import java.util.stream.Stream;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork;
import org.hiero.mirror.web3.repository.FileDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatesAndFeesLoaderTest {
    private static final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(200L))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(31)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .build())
            .build();
    private static final CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(200L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .build();
    private static final FileData exchangeRatesFileData = FileData.builder()
            .consensusTimestamp(200L)
            .fileData(exchangeRatesSet.toByteArray())
            .build();
    private static final FileData feeScheduleFileData = FileData.builder()
            .consensusTimestamp(200L)
            .fileData(feeSchedules.toByteArray())
            .build();
    private static final FileData fileDataCorrupt = FileData.builder()
            .consensusTimestamp(300L)
            .fileData("corrupt".getBytes())
            .build();

    @Mock
    private FileDataRepository fileDataRepository;

    private MirrorNodeEvmProperties evmProperties;
    private CommonProperties commonProperties;
    private SystemEntity systemEntity;
    private RatesAndFeesLoader subject;

    // Method that provides the test data
    public static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }

    @BeforeEach
    void setup() {
        commonProperties = new CommonProperties();
        systemEntity = new SystemEntity(commonProperties);
        evmProperties = new MirrorNodeEvmProperties(commonProperties, systemEntity);
        subject = new RatesAndFeesLoader(fileDataRepository, evmProperties, systemEntity);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadExchangeRates(long shard, long realm) {
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var exchangeRateEntityId = systemEntity.exchangeRateFile();
        when(fileDataRepository.getFileAtTimestamp(eq(exchangeRateEntityId.getId()), anyLong()))
                .thenReturn(Optional.of(exchangeRatesFileData));

        final var actual = subject.loadExchangeRates(250L);

        assertThat(actual).isEqualTo(exchangeRatesSet);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadDefaultExchangeRates(long shard, long realm) {
        evmProperties.setNetwork(HederaNetwork.OTHER);
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var exchangeRateEntityId = systemEntity.exchangeRateFile();
        when(fileDataRepository.getFileAtTimestamp(eq(exchangeRateEntityId.getId()), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadExchangeRates(100L);
        assertThat(actual).isEqualTo(RatesAndFeesLoader.DEFAULT_EXCHANGE_RATE_SET);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadEmptyExchangeRates(long shard, long realm) {
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var exchangeRateEntityId = systemEntity.exchangeRateFile();
        when(fileDataRepository.getFileAtTimestamp(eq(exchangeRateEntityId.getId()), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadExchangeRates(100L);
        assertThat(actual).isEqualTo(ExchangeRateSet.newBuilder().build());
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadWrongDataExchangeRates(long shard, long realm) {
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var exchangeRateEntityId = systemEntity.exchangeRateFile();
        var expectedMessage = String.format("Rates %s are corrupt!", exchangeRateEntityId);
        when(fileDataRepository.getFileAtTimestamp(eq(exchangeRateEntityId.getId()), anyLong()))
                .thenReturn(Optional.of(fileDataCorrupt));

        final var exception = assertThrows(IllegalStateException.class, () -> subject.loadExchangeRates(350L));

        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getFileForExchangeRatesFallback(long shard, long realm) {
        long currentNanos = 350L;
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var exchangeRateEntityId = systemEntity.exchangeRateFile();
        when(fileDataRepository.getFileAtTimestamp(exchangeRateEntityId.getId(), currentNanos))
                .thenReturn(Optional.of(fileDataCorrupt));
        when(fileDataRepository.getFileAtTimestamp(exchangeRateEntityId.getId(), 299L))
                .thenReturn(Optional.of(exchangeRatesFileData));

        var actual = subject.loadExchangeRates(currentNanos);
        assertThat(actual).isEqualTo(exchangeRatesSet);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadFeeSchedules(long shard, long realm) {
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var feeScheduleEntityId = systemEntity.feeScheduleFile();
        when(fileDataRepository.getFileAtTimestamp(eq(feeScheduleEntityId.getId()), anyLong()))
                .thenReturn(Optional.of(feeScheduleFileData));

        final var actual = subject.loadFeeSchedules(350L);

        assertThat(actual).isEqualTo(feeSchedules);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadDefaultFeeSchedules(long shard, long realm) {
        evmProperties.setNetwork(HederaNetwork.OTHER);
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var feeScheduleEntityId = systemEntity.feeScheduleFile();
        when(fileDataRepository.getFileAtTimestamp(eq(feeScheduleEntityId.getId()), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadFeeSchedules(100L);
        assertThat(actual).isEqualTo(RatesAndFeesLoader.DEFAULT_FEE_SCHEDULE);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadEmptyFeeSchedules(long shard, long realm) {
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var feeScheduleEntityId = systemEntity.feeScheduleFile();
        when(fileDataRepository.getFileAtTimestamp(eq(feeScheduleEntityId.getId()), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadFeeSchedules(100L);
        assertThat(actual).isEqualTo(CurrentAndNextFeeSchedule.newBuilder().build());
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadWrongDataFeeSchedules(long shard, long realm) {
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var feeScheduleEntityId = systemEntity.feeScheduleFile();
        var expectedMessage = String.format("Fee schedule %s is corrupt!", feeScheduleEntityId);
        when(fileDataRepository.getFileAtTimestamp(eq(feeScheduleEntityId.getId()), anyLong()))
                .thenReturn(Optional.of(fileDataCorrupt));

        final var exception = assertThrows(IllegalStateException.class, () -> subject.loadFeeSchedules(350L));

        assertThat(exception.getMessage()).isEqualTo(expectedMessage);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void getFileForFeeScheduleFallback(long shard, long realm) {
        long currentNanos = 350L;
        commonProperties.setRealm(realm);
        commonProperties.setShard(shard);
        var feeScheduleEntityId = systemEntity.feeScheduleFile();
        when(fileDataRepository.getFileAtTimestamp(feeScheduleEntityId.getId(), currentNanos))
                .thenReturn(Optional.of(fileDataCorrupt));
        when(fileDataRepository.getFileAtTimestamp(feeScheduleEntityId.getId(), 299L))
                .thenReturn(Optional.of(feeScheduleFileData));

        var actual = subject.loadFeeSchedules(currentNanos);
        assertThat(actual).isEqualTo(feeSchedules);
    }
}
