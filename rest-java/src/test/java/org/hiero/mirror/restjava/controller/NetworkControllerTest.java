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
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.rest.model.NetworkSupplyResponse;
import org.hiero.mirror.restjava.common.EntityIdParameter;
import org.hiero.mirror.restjava.config.NetworkProperties;
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
import org.springframework.data.domain.Sort;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClient.RequestHeadersUriSpec;

@RequiredArgsConstructor
final class NetworkControllerTest extends ControllerTest {

    private final CommonMapper commonMapper;
    private final ExchangeRateMapper exchangeRateMapper;
    private final FeeScheduleMapper feeScheduleMapper;
    private final NetworkStakeMapper networkStakeMapper;
    private final NetworkProperties networkProperties;
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
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, Sort.Direction.ASC);

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
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, Sort.Direction.DESC);

            // when
            final var actual = restClient
                    .get()
                    .uri("?order=" + Sort.Direction.DESC.name())
                    .retrieve()
                    .body(NetworkFeesResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(expected);
        }

        @Test
        void fallbackRecovers() {
            // given
            final var feeSchedule = systemFileFee();
            final var exchangeRate = systemFileExchangeRate();
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, Sort.Direction.ASC);
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

            final var expected0 = feeScheduleMapper.map(fee0, exchangeRate0, Sort.Direction.ASC);
            final var expected1 = feeScheduleMapper.map(fee1, exchangeRate1, Sort.Direction.ASC);
            // For fee2, use exchangeRate3 as it will be found by gte queries
            final var expected2 = feeScheduleMapper.map(fee2, exchangeRate3, Sort.Direction.ASC);
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

    @DisplayName("/api/v1/network/supply")
    @Nested
    final class SupplyEndpointTest extends EndpointTest {

        private static final long BALANCE_PER_ACCOUNT = 1_000_000 * DomainUtils.TINYBARS_IN_ONE_HBAR;

        @Override
        protected String getUrl() {
            return "network/supply";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            createUnreleasedSupplyAccounts(null);
            return uriSpec.uri("");
        }

        @Test
        void getNetworkSupplyNoQueryParams() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when
            final var response = restClient.get().uri("").retrieve().body(NetworkSupplyResponse.class);

            // then
            final var expectedUnreleasedSupply =
                    networkProperties.getUnreleasedSupplyAccountIds().size() * BALANCE_PER_ACCOUNT;
            final var actualUnreleasedSupply =
                    Long.parseLong(response.getTotalSupply()) - Long.parseLong(response.getReleasedSupply());

            assertThat(response).isNotNull();
            assertThat(response.getTotalSupply()).isEqualTo("5000000000000000000");
            assertThat(actualUnreleasedSupply).isEqualTo(expectedUnreleasedSupply);
        }

        @Test
        void withQueryParameterTotalcoins() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when
            final var response =
                    restClient.get().uri("?q=totalcoins").retrieve().toEntity(String.class);

            // then
            assertThat(response.getBody()).isEqualTo("50000000000.00000000");
            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().toString()).startsWith("text/plain");
        }

        @Test
        void withQueryParameterCirculating() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when
            final var response =
                    restClient.get().uri("?q=circulating").retrieve().toEntity(String.class);

            // then - 49,452,000,000 hbars
            assertThat(response.getBody()).isEqualTo("49452000000.00000000");
            assertThat(response.getHeaders().getContentType()).isNotNull();
            assertThat(response.getHeaders().getContentType().toString()).startsWith("text/plain");
        }

        @Test
        void withTimestamp() {
            // given
            final var timestamp = domainBuilder.timestamp();
            createUnreleasedSupplyAccounts(timestamp);

            // when
            final var response = restClient
                    .get()
                    .uri("?timestamp=" + commonMapper.mapTimestamp(timestamp))
                    .retrieve()
                    .body(NetworkSupplyResponse.class);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getTotalSupply()).isEqualTo("5000000000000000000");
            assertThat(response.getReleasedSupply()).isEqualTo("4945200000000000000");
            assertThat(response.getTimestamp()).isEqualTo(commonMapper.mapTimestamp(timestamp));
        }

        @Test
        void withTimestampLte() {
            // given
            final var timestamp1 = domainBuilder.timestamp();
            final var timestamp2 = domainBuilder.timestamp();
            createUnreleasedSupplyAccounts(timestamp1);
            createUnreleasedSupplyAccounts(timestamp2);

            // when
            final var response = restClient
                    .get()
                    .uri("?timestamp=lte:" + commonMapper.mapTimestamp(timestamp1))
                    .retrieve()
                    .body(NetworkSupplyResponse.class);

            // then - should return data at timestamp1
            assertThat(response).isNotNull();
            assertThat(response.getTimestamp()).isEqualTo(commonMapper.mapTimestamp(timestamp1));
        }

        @Test
        void invalidQueryParameter() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when/then
            validateError(
                    () -> restClient.get().uri("?q=invalid").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid parameter: 'q'. Valid values: totalcoins, circulating");
        }

        @Test
        void multipleTimestamps() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when/then
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
        void invalidTimestamp() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when/then
            validateError(
                    () -> restClient.get().uri("?timestamp=invalid").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'timestamp'");
        }

        @Test
        void notFoundWithTimestamp() {
            // given
            final var timestamp = domainBuilder.timestamp();

            // when/then - no data for this timestamp
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=" + commonMapper.mapTimestamp(timestamp))
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "Network supply not found");
        }

        @Test
        void notFoundNoAccounts() {
            // when/then - no accounts in database
            validateError(
                    () -> restClient.get().uri("").retrieve().toEntity(String.class),
                    HttpClientErrorException.NotFound.class,
                    "Network supply not found");
        }

        @Test
        void invalidParametersBounds() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when/then - invalid range (lower > upper)
            validateError(
                    () -> restClient
                            .get()
                            .uri("?timestamp=gt:2&timestamp=lt:1")
                            .retrieve()
                            .toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid range provided for timestamp");
        }

        @Test
        void invalidParametersNe() {
            // given
            createUnreleasedSupplyAccounts(null);

            // when/then - ne: operator not supported
            validateError(
                    () -> restClient.get().uri("?timestamp=ne:1").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'timestamp'");
        }

        @ParameterizedTest
        @CsvSource({
            "?timestamp={1}, 1",
            "?timestamp={2}, 2",
            "?timestamp=eq:{1}, 1",
            "?timestamp=eq:{2}, 2",
            "?timestamp=lte:{1}, 1",
            "?timestamp=lte:{2}, 2",
            "?timestamp=lt:{2}, 1",
            "?timestamp=gte:{1}&timestamp=lte:{1}, 1",
            "?timestamp=gte:{1}&timestamp=lte:{2}, 2",
            "?timestamp=gt:{0}&timestamp=lt:{2}, 1"
        })
        void timestampBounds(String parameters, int expectedIndex) {
            // given - create account balances at different timestamps
            final var timestamp1 = domainBuilder.timestamp();
            final var timestamp2 = domainBuilder.timestamp();
            createUnreleasedSupplyAccounts(timestamp1);
            createUnreleasedSupplyAccounts(timestamp2);
            final var timestamp0 = timestamp1 - 1000000;

            final var formattedParams = MessageFormat.format(
                    parameters,
                    commonMapper.mapTimestamp(timestamp0),
                    commonMapper.mapTimestamp(timestamp1),
                    commonMapper.mapTimestamp(timestamp2));

            final var expected0Timestamp = commonMapper.mapTimestamp(timestamp0);
            final var expected1Timestamp = commonMapper.mapTimestamp(timestamp1);
            final var expected2Timestamp = commonMapper.mapTimestamp(timestamp2);
            final var expectedTimestamps = List.of(expected0Timestamp, expected1Timestamp, expected2Timestamp);

            // when
            final var response =
                    restClient.get().uri(formattedParams).retrieve().body(NetworkSupplyResponse.class);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getTimestamp()).isEqualTo(expectedTimestamps.get(expectedIndex));
        }

        @Test
        void timestampDeduplicated() {
            // given - multiple balances at different timestamps, ensure latest is used
            final var timestamp1 = 1_600_000_000_000_000_000L;
            final var timestamp2 = 1_700_000_000_000_000_000L;
            final var timestamp3 = 1_700_000_000_000_000_005L;

            // Create different balances at different timestamps for same accounts
            createCustomBalance(2, 10L, timestamp1);
            createCustomBalance(42, 20L, timestamp1);
            createCustomBalance(2, 1L, timestamp2);
            createCustomBalance(42, 1L, timestamp2);
            createCustomBalance(2, 4_000_000_000_000_000_000L, timestamp3);
            createCustomBalance(42, 50L, timestamp3);

            // when - query for timestamp range that includes latest data
            final var response = restClient
                    .get()
                    .uri("?timestamp=lte:1700000000.000000000")
                    .retrieve()
                    .body(NetworkSupplyResponse.class);

            // then - should use latest balances within range (timestamp2)
            assertThat(response).isNotNull();
            assertThat(response.getTimestamp()).isEqualTo("1700000000.000000000");
        }

        private void createCustomBalance(long accountNum, long balance, long timestamp) {
            final var entityIdParameter = EntityIdParameter.valueOf(String.valueOf(accountNum));
            final var accountId = EntityId.of(entityIdParameter.shard(), entityIdParameter.realm(), accountNum);
            domainBuilder
                    .accountBalance()
                    .customize(ab -> ab.balance(balance).id(new AccountBalance.Id(timestamp, accountId)))
                    .persist();
        }

        private void createUnreleasedSupplyAccounts(Long timestamp) {
            for (final var accountId : networkProperties.getUnreleasedSupplyAccountIds()) {
                if (timestamp != null) {
                    domainBuilder
                            .accountBalance()
                            .customize(ab -> ab.balance(BALANCE_PER_ACCOUNT)
                                    .id(new AccountBalance.Id(timestamp, EntityId.of(accountId))))
                            .persist();
                } else {
                    domainBuilder
                            .entity()
                            .customize(e -> e.id(accountId)
                                    .balance(BALANCE_PER_ACCOUNT)
                                    .balanceTimestamp(domainBuilder.timestamp()))
                            .persist();
                }
            }
        }
    }
}
