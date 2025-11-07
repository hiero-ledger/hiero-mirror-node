// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.text.MessageFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.mapper.CommonMapper;
import org.hiero.mirror.restjava.mapper.ExchangeRateMapper;
import org.hiero.mirror.restjava.mapper.FeeScheduleMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
final class NetworkControllerTest extends ControllerTest {

    private final CommonMapper commonMapper;
    private final ExchangeRateMapper exchangeRateMapper;
    private final FeeScheduleMapper feeScheduleMapper;
    private final NetworkStakeMapper networkStakeMapper;
    private final SystemEntity systemEntity;

    @DisplayName("/api/v1/network/exchangerate")
    @Nested
    final class ExchangeRateEndpointTest extends EndpointTest {

        private final EntityId fileId = systemEntity.exchangeRateFile();

        @Override
        protected String getUrl() {
            return "network/exchangerate";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            final var exchangeRateSet = exchangeRateSet(1);
            exchangeRateFile(exchangeRateSet.toByteArray());
            return uriSpec.uri("");
        }

        @Test
        void success() {
            // given
            final var expected = exchangeRateMapper.map(systemFile());

            // when
            final var actual = restClient.get().uri("").retrieve().body(NetworkExchangeRateSetResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected);
        }

        @Test
        void fallbackRecovers() {
            // given
            final var expected = exchangeRateMapper.map(systemFile());
            exchangeRateFile(domainBuilder.bytes(100)); // The latest file is corrupt and is skipped

            // when
            final var actual = restClient.get().uri("").retrieve().body(NetworkExchangeRateSetResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
            "?timestamp={1}, 1",
            "?timestamp={2}, 2",
            "?timestamp=eq:{0}, 0",
            "?timestamp=eq:{1}, 1",
            "?timestamp=eq:{2}, 2",
            "?timestamp=gt:{0}, 2",
            "?timestamp=gt:{1}, 2",
            "?timestamp=gte:{1}, 2",
            "?timestamp=gte:{2}, 2",
            "?timestamp=lt:{1}, 0",
            "?timestamp=lt:{2}, 1",
            "?timestamp=lte:{1}, 1",
            "?timestamp=gt:{0}&timestamp=eq:{2}, 2",
            "?timestamp=eq:{1}&timestamp=eq:{2}, 2",
            "?timestamp=lt:{0}&timestamp=eq:{2}, 2",
            "?timestamp=gt:{0}&timestamp=lt:{2}, 1",
            "?timestamp=gte:{1}&timestamp=lt:{2}, 1",
            "?timestamp=gte:{1}&timestamp=lte:{1}, 1",
            "?timestamp=gt:{0}&timestamp=lte:{1}, 1"
        })
        void timestampBounds(String parameters, int expectedIndex) {
            // given
            final var exchangeRate0 = systemFile();
            final var exchangeRate1 = systemFile();
            final var exchangeRate2 = systemFile();
            final var formattedParams = MessageFormat.format(
                    parameters,
                    commonMapper.mapTimestamp(exchangeRate0.fileData().getConsensusTimestamp()),
                    commonMapper.mapTimestamp(exchangeRate1.fileData().getConsensusTimestamp()),
                    commonMapper.mapTimestamp(exchangeRate2.fileData().getConsensusTimestamp()));

            final var expected0 = exchangeRateMapper.map(exchangeRate0);
            final var expected1 = exchangeRateMapper.map(exchangeRate1);
            final var expected2 = exchangeRateMapper.map(exchangeRate2);
            final var expected = List.of(expected0, expected1, expected2);

            // when
            final var actual =
                    restClient.get().uri(formattedParams).retrieve().body(NetworkExchangeRateSetResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected.get(expectedIndex));
        }

        @Test
        void multipleTimestamps() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=1&timestamp=lt:2&timestamp=gt:0")
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "timestamp size must be between 0 and 2");
        }

        @Test
        void invalidParameters() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=test&timestamp=%3cscript%3ealert(1)%3c%2fscript%3e")
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'timestamp'");
        }

        @Test
        void timestampNotEquals() {
            validateError(
                    () -> restClient.get().uri("?timestamp=ne:1").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'timestamp'");
        }

        @Test
        void fallbackRetriesExceeded() {
            // given
            exchangeRateMapper.map(systemFile()); // Fails before reaching the valid file
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));
            exchangeRateFile(domainBuilder.bytes(100));

            // when/then
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "File %s not found".formatted(fileId));
        }

        @Test
        void unserializableContents() {
            exchangeRateFile(domainBuilder.bytes(100));
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "File %s not found".formatted(fileId));
        }

        @Test
        void notFound() {
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "File %s not found".formatted(fileId));
        }

        private FileData exchangeRateFile(final byte[] bytes) {
            return domainBuilder
                    .fileData()
                    .customize(f -> f.entityId(fileId).fileData(bytes))
                    .persist();
        }

        private ExchangeRateSet exchangeRateSet(int hbars) {
            return ExchangeRateSet.newBuilder()
                    .setCurrentRate(ExchangeRate.newBuilder()
                            .setCentEquiv(12)
                            .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(1759951090L))
                            .setHbarEquiv(hbars))
                    .setNextRate(ExchangeRate.newBuilder()
                            .setCentEquiv(15)
                            .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(1759972690L))
                            .setHbarEquiv(hbars))
                    .build();
        }

        private SystemFile<ExchangeRateSet> systemFile() {
            final var exchangeRateSet = exchangeRateSet((int) domainBuilder.number());
            return new SystemFile<>(exchangeRateFile(exchangeRateSet.toByteArray()), exchangeRateSet);
        }
    }

    @DisplayName("/api/v1/network/fees")
    @Nested
    final class FeesEndpointTest extends EndpointTest {

        private final EntityId feeFileId = systemEntity.feeScheduleFile();
        private final EntityId exchangeRateFileId = systemEntity.exchangeRateFile();

        @Override
        protected String getUrl() {
            return "network/fees";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            final var feeSchedule = feeSchedule();
            final var exchangeRateSet = exchangeRateSet(1);
            feeScheduleFile(feeSchedule.toByteArray());
            exchangeRateFile(exchangeRateSet.toByteArray());
            return uriSpec.uri("");
        }

        @Test
        void success() {
            // given
            final var feeSchedule = systemFileFee();
            final var exchangeRate = systemFileExchangeRate();
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, "asc", commonMapper);

            // when
            final var actual = restClient.get().uri("").retrieve().body(NetworkFeesResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected);
        }

        @Test
        void successWithOrder() {
            // given
            final var feeSchedule = systemFileFee();
            final var exchangeRate = systemFileExchangeRate();
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, "desc", commonMapper);

            // when
            final var actual = restClient.get().uri("?order=desc").retrieve().body(NetworkFeesResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected);
        }

        @Test
        void fallbackRecovers() {
            // given
            final var feeSchedule = systemFileFee();
            final var exchangeRate = systemFileExchangeRate();
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, "asc", commonMapper);
            feeScheduleFile(domainBuilder.bytes(100)); // The latest file is corrupt and is skipped

            // when
            final var actual = restClient.get().uri("").retrieve().body(NetworkFeesResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
            "?timestamp={1}, 1",
            "?timestamp={2}, 2",
            "?timestamp=eq:{0}, 0",
            "?timestamp=eq:{1}, 1",
            "?timestamp=eq:{2}, 2",
            "?timestamp=gt:{0}, 2",
            "?timestamp=gt:{1}, 2",
            "?timestamp=gte:{1}, 2",
            "?timestamp=gte:{2}, 2",
            "?timestamp=lt:{1}, 0",
            "?timestamp=lt:{2}, 1",
            "?timestamp=lte:{1}, 1",
            "?timestamp=gt:{0}&timestamp=eq:{2}, 2",
            "?timestamp=eq:{1}&timestamp=eq:{2}, 2",
            "?timestamp=lt:{0}&timestamp=eq:{2}, 2",
            "?timestamp=gt:{0}&timestamp=lt:{2}, 1",
            "?timestamp=gte:{1}&timestamp=lt:{2}, 1",
            "?timestamp=gt:{0}&timestamp=lte:{1}, 1"
        })
        void timestampBounds(String parameters, int expectedIndex) {
            // given - create matching pairs of exchange rates and fee schedules
            final var exchangeRate0 = systemFileExchangeRate();
            final var fee0 = systemFileFee();
            final var exchangeRate1 = systemFileExchangeRate();
            final var fee1 = systemFileFee();
            final var exchangeRate2 = systemFileExchangeRate();
            final var fee2 = systemFileFee();
            // Create one more exchange rate after fee2 to handle gte queries on fee2
            final var exchangeRate3 = systemFileExchangeRate();

            final var formattedParams = MessageFormat.format(
                    parameters,
                    commonMapper.mapTimestamp(fee0.fileData().getConsensusTimestamp()),
                    commonMapper.mapTimestamp(fee1.fileData().getConsensusTimestamp()),
                    commonMapper.mapTimestamp(fee2.fileData().getConsensusTimestamp()));

            final var expected0 = feeScheduleMapper.map(fee0, exchangeRate0, "asc", commonMapper);
            final var expected1 = feeScheduleMapper.map(fee1, exchangeRate1, "asc", commonMapper);
            // For fee2, use exchangeRate3 as it will be found by gte queries
            final var expected2 = feeScheduleMapper.map(fee2, exchangeRate3, "asc", commonMapper);
            final var expected = List.of(expected0, expected1, expected2);

            // when
            final var actual = restClient.get().uri(formattedParams).retrieve().body(NetworkFeesResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected.get(expectedIndex));
        }

        @Test
        void multipleTimestamps() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=1&timestamp=lt:2&timestamp=gt:0")
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "timestamp size must be between 0 and 2");
        }

        @Test
        void invalidParameters() {
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=test&timestamp=%3cscript%3ealert(1)%3c%2fscript%3e")
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'timestamp'");
        }

        @Test
        void timestampNotEquals() {
            validateError(
                    () -> restClient.get().uri("?timestamp=ne:1").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'timestamp'");
        }

        @Test
        void fallbackRetriesExceeded() {
            // given
            systemFileFee(); // Create valid file first
            systemFileExchangeRate(); // Create valid exchange rate
            // Add 10 corrupt files - retry logic will fail before reaching the valid file
            for (int i = 0; i < 10; i++) {
                feeScheduleFile(domainBuilder.bytes(100));
                exchangeRateFile(exchangeRateSet(1).toByteArray());
            }

            // when/then
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "File %s not found".formatted(feeFileId));
        }

        @Test
        void unserializableContents() {
            feeScheduleFile(domainBuilder.bytes(100));
            exchangeRateFile(exchangeRateSet(1).toByteArray());
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "File %s not found".formatted(feeFileId));
        }

        @Test
        void notFound() {
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "File %s not found".formatted(feeFileId));
        }

        private FileData feeScheduleFile(final byte[] bytes) {
            return domainBuilder
                    .fileData()
                    .customize(f -> f.entityId(feeFileId).fileData(bytes))
                    .persist();
        }

        private FileData exchangeRateFile(final byte[] bytes) {
            return domainBuilder
                    .fileData()
                    .customize(f -> f.entityId(exchangeRateFileId).fileData(bytes))
                    .persist();
        }

        private CurrentAndNextFeeSchedule feeSchedule() {
            return CurrentAndNextFeeSchedule.newBuilder()
                    .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                            .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                    .setHederaFunctionality(HederaFunctionality.ContractCall)
                                    .addFees(FeeData.newBuilder()
                                            .setServicedata(FeeComponents.newBuilder()
                                                    .setGas(852000L)
                                                    .build())
                                            .build())
                                    .build())
                            .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                    .setHederaFunctionality(HederaFunctionality.ContractCreate)
                                    .addFees(FeeData.newBuilder()
                                            .setServicedata(FeeComponents.newBuilder()
                                                    .setGas(1068000L)
                                                    .build())
                                            .build())
                                    .build())
                            .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                    .setHederaFunctionality(HederaFunctionality.EthereumTransaction)
                                    .addFees(FeeData.newBuilder()
                                            .setServicedata(FeeComponents.newBuilder()
                                                    .setGas(953000L)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();
        }

        private ExchangeRateSet exchangeRateSet(int hbars) {
            return ExchangeRateSet.newBuilder()
                    .setCurrentRate(ExchangeRate.newBuilder()
                            .setCentEquiv(12)
                            .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(1759951090L))
                            .setHbarEquiv(hbars))
                    .setNextRate(ExchangeRate.newBuilder()
                            .setCentEquiv(15)
                            .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(1759972690L))
                            .setHbarEquiv(hbars))
                    .build();
        }

        private SystemFile<CurrentAndNextFeeSchedule> systemFileFee() {
            final var feeSchedule = feeSchedule();
            return new SystemFile<>(feeScheduleFile(feeSchedule.toByteArray()), feeSchedule);
        }

        private SystemFile<ExchangeRateSet> systemFileExchangeRate() {
            final var exchangeRateSet = exchangeRateSet(1); // Use consistent hbarEquiv for predictable gas calculations
            return new SystemFile<>(exchangeRateFile(exchangeRateSet.toByteArray()), exchangeRateSet);
        }
    }

    @DisplayName("/api/v1/network/stake")
    @Nested
    final class NetworkStakeEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "network/stake";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            domainBuilder.networkStake().persist();
            return uriSpec.uri("");
        }

        @Test
        void success() {
            // given
            final var networkStake = domainBuilder.networkStake().persist();
            final var expected = networkStakeMapper.map(networkStake);

            // when
            final var actual = restClient.get().uri("").retrieve().body(NetworkStakeResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected);
        }

        @Test
        void notFound() {
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "No network stake data found");
        }
    }
}
