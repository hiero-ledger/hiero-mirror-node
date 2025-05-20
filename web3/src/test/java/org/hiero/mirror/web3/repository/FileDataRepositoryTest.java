// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.repository;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class FileDataRepositoryTest extends Web3IntegrationTest {

    private static final long EXPIRY = 1_234_567_890L;
    private static final byte[] EXCHANGE_RATES_SET = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(1)
                    .setHbarEquiv(12)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(EXPIRY))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(31)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .build())
            .build()
            .toByteArray();

    private static final byte[] EXCHANGE_RATES_SET_200 = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(2)
                    .setHbarEquiv(13)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(200))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(3)
                    .setHbarEquiv(32)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_893L))
                    .build())
            .build()
            .toByteArray();

    private static final byte[] EXCHANGE_RATES_SET_300 = ExchangeRateSet.newBuilder()
            .setCurrentRate(ExchangeRate.newBuilder()
                    .setCentEquiv(3)
                    .setHbarEquiv(14)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(300))
                    .build())
            .setNextRate(ExchangeRate.newBuilder()
                    .setCentEquiv(4)
                    .setHbarEquiv(33)
                    .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_893L))
                    .build())
            .build()
            .toByteArray();

    private static final byte[] FEE_SCHEDULES = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(EXPIRY))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .setNextFeeSchedule(FeeSchedule.newBuilder()
                    .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(2_234_567_890L))
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .setHederaFunctionality(ContractCall)
                            .addFees(FeeData.newBuilder().build())))
            .build()
            .toByteArray();

    private final FileDataRepository fileDataRepository;

    @Test
    void getHistoricalFileForExchangeRates() {
        var exchangeRateFileId = systemEntity.exchangeRateFile();
        var expected1 = domainBuilder
                .fileData()
                .customize(f -> f.fileData(EXCHANGE_RATES_SET_200)
                        .entityId(exchangeRateFileId)
                        .consensusTimestamp(200L))
                .persist();
        var expected2 = domainBuilder
                .fileData()
                .customize(f -> f.fileData(EXCHANGE_RATES_SET_300)
                        .entityId(exchangeRateFileId)
                        .consensusTimestamp(300L))
                .persist();

        assertThat(fileDataRepository.getFileAtTimestamp(exchangeRateFileId.getId(), 301))
                .get()
                .usingRecursiveComparison()
                .ignoringFields("transactionType")
                .isEqualTo(expected2);
        assertThat(fileDataRepository.getFileAtTimestamp(exchangeRateFileId.getId(), 300))
                .get()
                .usingRecursiveComparison()
                .ignoringFields("transactionType")
                .isEqualTo(expected2);

        assertThat(fileDataRepository.getFileAtTimestamp(exchangeRateFileId.getId(), 299))
                .get()
                .usingRecursiveComparison()
                .ignoringFields("transactionType")
                .isEqualTo(expected1);
        assertThat(fileDataRepository.getFileAtTimestamp(exchangeRateFileId.getId(), 199))
                .isEmpty();
    }

    @Test
    void getFileForExchangeRates() {
        var exchangeRateFileId = systemEntity.exchangeRateFile();
        var expected = domainBuilder
                .fileData()
                .customize(f -> f.fileData(EXCHANGE_RATES_SET)
                        .entityId(exchangeRateFileId)
                        .consensusTimestamp(EXPIRY))
                .persist();

        assertThat(fileDataRepository.getFileAtTimestamp(exchangeRateFileId.getId(), EXPIRY))
                .get()
                .usingRecursiveComparison()
                .ignoringFields("transactionType")
                .isEqualTo(expected);
    }

    @Test
    void getFileForFeeSchedules() {
        var feeScheduleFileId = systemEntity.feeScheduleFile();
        var expected = domainBuilder
                .fileData()
                .customize(f ->
                        f.fileData(FEE_SCHEDULES).entityId(feeScheduleFileId).consensusTimestamp(EXPIRY))
                .persist();

        assertThat(fileDataRepository.getFileAtTimestamp(feeScheduleFileId.getId(), EXPIRY))
                .get()
                .usingRecursiveComparison()
                .ignoringFields("transactionType")
                .isEqualTo(expected);
    }
}
