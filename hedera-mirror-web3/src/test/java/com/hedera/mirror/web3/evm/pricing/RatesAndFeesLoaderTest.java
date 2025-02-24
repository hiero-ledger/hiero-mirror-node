// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.evm.pricing;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties.HederaNetwork;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    private static final String CORRUPT_RATES_MESSAGE = "Rates 0.0.112 are corrupt!";
    private static final long EXCHANGE_RATES_ID = 112L;
    private static final String CORRUPT_SCHEDULES_MESSAGE = "Fee schedule 0.0.111 is corrupt!";
    private static final long FEE_SCHEDULES_ID = 111L;
    private static final long EXCHANGE_RATE_ID = 112L;

    @Mock
    private FileDataRepository fileDataRepository;

    @Mock
    private MirrorNodeEvmProperties evmProperties;

    @InjectMocks
    private RatesAndFeesLoader subject;

    @BeforeEach
    void setup() {
        when(evmProperties.getNetwork()).thenReturn(HederaNetwork.TESTNET);
    }

    @Test
    void loadExchangeRates() {
        when(fileDataRepository.getFileAtTimestamp(eq(EXCHANGE_RATES_ID), anyLong()))
                .thenReturn(Optional.of(exchangeRatesFileData));

        final var actual = subject.loadExchangeRates(250L);

        assertThat(actual).isEqualTo(exchangeRatesSet);
    }

    @Test
    void loadDefaultExchangeRates() {
        when(evmProperties.getNetwork()).thenReturn(HederaNetwork.OTHER);
        when(fileDataRepository.getFileAtTimestamp(eq(EXCHANGE_RATES_ID), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadExchangeRates(100L);
        assertThat(actual).isEqualTo(RatesAndFeesLoader.DEFAULT_EXCHANGE_RATE_SET);
    }

    @Test
    void loadEmptyExchangeRates() {
        when(fileDataRepository.getFileAtTimestamp(eq(EXCHANGE_RATES_ID), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadExchangeRates(100L);
        assertThat(actual).isEqualTo(ExchangeRateSet.newBuilder().build());
    }

    @Test
    void loadWrongDataExchangeRates() {
        when(fileDataRepository.getFileAtTimestamp(eq(EXCHANGE_RATES_ID), anyLong()))
                .thenReturn(Optional.of(fileDataCorrupt));

        final var exception = assertThrows(IllegalStateException.class, () -> subject.loadExchangeRates(350L));

        assertThat(exception.getMessage()).isEqualTo(CORRUPT_RATES_MESSAGE);
    }

    @Test
    void getFileForExchangeRatesFallback() {
        long currentNanos = 350L;
        when(fileDataRepository.getFileAtTimestamp(EXCHANGE_RATE_ID, currentNanos))
                .thenReturn(Optional.of(fileDataCorrupt));
        when(fileDataRepository.getFileAtTimestamp(EXCHANGE_RATE_ID, 299L))
                .thenReturn(Optional.of(exchangeRatesFileData));

        var actual = subject.loadExchangeRates(currentNanos);
        assertThat(actual).isEqualTo(exchangeRatesSet);
    }

    @Test
    void loadFeeSchedules() {
        when(fileDataRepository.getFileAtTimestamp(eq(FEE_SCHEDULES_ID), anyLong()))
                .thenReturn(Optional.of(feeScheduleFileData));

        final var actual = subject.loadFeeSchedules(350L);

        assertThat(actual).isEqualTo(feeSchedules);
    }

    @Test
    void loadDefaultFeeSchedules() {
        when(evmProperties.getNetwork()).thenReturn(HederaNetwork.OTHER);
        when(fileDataRepository.getFileAtTimestamp(eq(FEE_SCHEDULES_ID), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadFeeSchedules(100L);
        assertThat(actual).isEqualTo(RatesAndFeesLoader.DEFAULT_FEE_SCHEDULE);
    }

    @Test
    void loadEmptyFeeSchedules() {
        when(fileDataRepository.getFileAtTimestamp(eq(FEE_SCHEDULES_ID), anyLong()))
                .thenReturn(Optional.empty());

        final var actual = subject.loadFeeSchedules(100L);
        assertThat(actual).isEqualTo(CurrentAndNextFeeSchedule.newBuilder().build());
    }

    @Test
    void loadWrongDataFeeSchedules() {
        when(fileDataRepository.getFileAtTimestamp(eq(FEE_SCHEDULES_ID), anyLong()))
                .thenReturn(Optional.of(fileDataCorrupt));

        final var exception = assertThrows(IllegalStateException.class, () -> subject.loadFeeSchedules(350L));

        assertThat(exception.getMessage()).isEqualTo(CORRUPT_SCHEDULES_MESSAGE);
    }

    @Test
    void getFileForFeeScheduleFallback() {
        long currentNanos = 350L;
        when(fileDataRepository.getFileAtTimestamp(FEE_SCHEDULES_ID, currentNanos))
                .thenReturn(Optional.of(fileDataCorrupt));
        when(fileDataRepository.getFileAtTimestamp(FEE_SCHEDULES_ID, 299L))
                .thenReturn(Optional.of(feeScheduleFileData));

        var actual = subject.loadFeeSchedules(currentNanos);
        assertThat(actual).isEqualTo(feeSchedules);
    }
}
