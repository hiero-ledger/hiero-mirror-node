// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.text.MessageFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.addressbook.AddressBookEntry;
import org.hiero.mirror.common.domain.balance.AccountBalance;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.rest.model.FeeEstimateResponse;
import org.hiero.mirror.rest.model.NetworkExchangeRateSetResponse;
import org.hiero.mirror.rest.model.NetworkFeesResponse;
import org.hiero.mirror.rest.model.NetworkStakeResponse;
import org.hiero.mirror.rest.model.NetworkSupplyResponse;
import org.hiero.mirror.restjava.common.Constants;
import org.hiero.mirror.restjava.common.RangeOperator;
import org.hiero.mirror.restjava.config.NetworkProperties;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.mapper.CommonMapper;
import org.hiero.mirror.restjava.mapper.ExchangeRateMapper;
import org.hiero.mirror.restjava.mapper.FeeScheduleMapper;
import org.hiero.mirror.restjava.mapper.NetworkStakeMapper;
import org.hiero.mirror.restjava.parameter.EntityIdParameter;
import org.hiero.mirror.restjava.parameter.TimestampParameter;
import org.hiero.mirror.restjava.service.Bound;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
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

    @DisplayName("GET /api/v1/network/fees")
    @Nested
    final class FeesEndpointTest extends EndpointTest {

        private static final long CURRENT_RATE_EXPIRATION_NANOS = 1759951090L * DomainUtils.NANOS_PER_SECOND;
        private long feeFileTimestampSeq = 0;

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
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, Bound.EMPTY, Sort.Direction.ASC);

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
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, Bound.EMPTY, Sort.Direction.DESC);

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
            final var expected = feeScheduleMapper.map(feeSchedule, exchangeRate, Bound.EMPTY, Sort.Direction.ASC);
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

            final var bound0 = boundForTimestamp(fee0.fileData().getConsensusTimestamp());
            final var bound1 = boundForTimestamp(fee1.fileData().getConsensusTimestamp());
            final var bound2 = boundForTimestamp(fee2.fileData().getConsensusTimestamp());
            final var expected0 = feeScheduleMapper.map(fee0, exchangeRate0, bound0, Sort.Direction.ASC);
            final var expected1 = feeScheduleMapper.map(fee1, exchangeRate1, bound1, Sort.Direction.ASC);
            // For fee2, use exchangeRate3 as it will be found by gte queries
            final var expected2 = feeScheduleMapper.map(fee2, exchangeRate3, bound2, Sort.Direction.ASC);
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

        @Disabled("Both GET and POST are supported on /network/fees")
        @Override
        @Test
        void methodNotAllowed() {
            // ignore
        }

        private FileData feeScheduleFile(final byte[] bytes) {
            final var timestamp =
                    CURRENT_RATE_EXPIRATION_NANOS - DomainUtils.NANOS_PER_SECOND + (feeFileTimestampSeq++);
            return domainBuilder
                    .fileData()
                    .customize(f -> f.entityId(feeFileId).fileData(bytes).consensusTimestamp(timestamp))
                    .persist();
        }

        private FileData exchangeRateFile(final byte[] bytes) {
            final var timestamp =
                    CURRENT_RATE_EXPIRATION_NANOS - DomainUtils.NANOS_PER_SECOND + (feeFileTimestampSeq++);
            return domainBuilder
                    .fileData()
                    .customize(
                            f -> f.entityId(exchangeRateFileId).fileData(bytes).consensusTimestamp(timestamp))
                    .persist();
        }

        private Bound boundForTimestamp(long timestampNanos) {
            return Bound.of(
                    new TimestampParameter[] {new TimestampParameter(RangeOperator.LTE, timestampNanos)},
                    Constants.TIMESTAMP,
                    org.hiero.mirror.restjava.jooq.domain.tables.FileData.FILE_DATA.CONSENSUS_TIMESTAMP);
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

    @DisplayName("POST /api/v1/network/fees")
    @Nested
    final class FeesEstimateEndpointTest extends RestTest {

        @Override
        protected String getUrl() {
            return "network/fees";
        }

        @ParameterizedTest
        @ValueSource(strings = {"protobuf", "x-protobuf"})
        void success(String mediaType) {
            // given
            final var transaction = transaction();

            // when
            final var actual = restClient
                    .post()
                    .uri("")
                    .body(transaction)
                    .contentType(new MediaType("application", mediaType))
                    .retrieve()
                    .body(FeeEstimateResponse.class);

            // then
            assertThat(actual).isNotNull().isEqualTo(NetworkController.FEE_ESTIMATE_RESPONSE);
        }

        @Test
        void nullBody() {
            // when / then
            validateError(
                    () -> restClient
                            .post()
                            .uri("")
                            .contentType(MediaType.APPLICATION_PROTOBUF)
                            .retrieve()
                            .body(FeeEstimateResponse.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to read request");
        }

        @Test
        void emptyBody() {
            // given
            final var transaction = new byte[0];

            // when / then
            validateError(
                    () -> restClient
                            .post()
                            .uri("")
                            .body(transaction)
                            .contentType(MediaType.APPLICATION_PROTOBUF)
                            .retrieve()
                            .body(FeeEstimateResponse.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to read request");
        }

        @Test
        void invalidMode() {
            // given
            final var transaction = transaction();

            // when / then
            validateError(
                    () -> restClient
                            .post()
                            .uri("?mode=invalid")
                            .body(transaction)
                            .contentType(MediaType.APPLICATION_PROTOBUF)
                            .retrieve()
                            .body(FeeEstimateResponse.class),
                    HttpClientErrorException.BadRequest.class,
                    "Failed to convert 'mode'");
        }

        @Test
        void invalidSignedTransaction() {
            // given
            final var bytes = DomainUtils.fromBytes(domainBuilder.bytes(100));
            final var transaction = Transaction.newBuilder()
                    .setSignedTransactionBytes(bytes)
                    .build()
                    .toByteArray();

            // when / then
            validateError(
                    () -> restClient
                            .post()
                            .uri("")
                            .body(transaction)
                            .contentType(MediaType.APPLICATION_PROTOBUF)
                            .retrieve()
                            .body(FeeEstimateResponse.class),
                    HttpClientErrorException.BadRequest.class,
                    "Unable to parse SignedTransaction");
        }

        @Test
        void unsupportedMediaType() {
            // given
            final var transaction = transaction();

            // when / then
            validateError(
                    () -> restClient
                            .post()
                            .uri("")
                            .body(transaction)
                            .contentType(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .body(FeeEstimateResponse.class),
                    HttpClientErrorException.UnsupportedMediaType.class,
                    "Content-Type 'application/json' is not supported");
        }

        private byte[] transaction() {
            final var transactionBody =
                    TransactionBody.newBuilder().setMemo("test").build().toByteString();
            final var signedTransaction = SignedTransaction.newBuilder()
                    .setBodyBytes(transactionBody)
                    .build()
                    .toByteString();
            return Transaction.newBuilder()
                    .setSignedTransactionBytes(signedTransaction)
                    .build()
                    .toByteArray();
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

        @Getter(lazy = true)
        private final Set<Long> unreleasedSupplyAccountIds = createUnreleasedSupplyAccountIds();

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
            final var expectedUnreleasedSupply = getUnreleasedSupplyAccountIds().size() * BALANCE_PER_ACCOUNT;
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
                    "Failed to convert 'q'");
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
            for (final var accountId : getUnreleasedSupplyAccountIds()) {
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

        private Set<Long> createUnreleasedSupplyAccountIds() {
            final var commonProperties = CommonProperties.getInstance();
            final var shard = commonProperties.getShard();
            final var realm = commonProperties.getRealm();
            final var accountIds = new TreeSet<Long>();

            for (final var range : networkProperties.getUnreleasedSupplyAccounts()) {
                for (long num = range.from(); num <= range.to(); num++) {
                    accountIds.add(EntityId.of(shard, realm, num).getId());
                }
            }

            return accountIds;
        }
    }

    @DisplayName("GET /api/v1/network/nodes")
    @Nested
    final class NodesEndpointTest extends EndpointTest {

        @Override
        protected String getUrl() {
            return "network/nodes";
        }

        @Override
        protected RequestHeadersSpec<?> defaultRequest(RequestHeadersUriSpec<?> uriSpec) {
            setupNetworkNodeData();
            return uriSpec.uri("");
        }

        @Test
        void success() {
            // given
            var nodes = setupNetworkNodeData();

            // when
            final var actual =
                    restClient.get().uri("").retrieve().body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(3);
            assertThat(actual.getLinks()).isNotNull();
            assertThat(actual.getLinks().getNext()).isNull(); // All results fit in one page
        }

        @Test
        void withFileId() {
            // given
            var timestamp = domainBuilder.timestamp();
            var addressBook = domainBuilder
                    .addressBook()
                    .customize(ab -> ab.startConsensusTimestamp(timestamp))
                    .persist();
            var fileId = addressBook.getFileId().getId();

            // Create 3 network nodes linked to this address book
            domainBuilder
                    .addressBookEntry(2)
                    .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                    .persist();
            domainBuilder
                    .addressBookEntry(2)
                    .customize(e -> e.consensusTimestamp(timestamp).nodeId(2L))
                    .persist();
            domainBuilder
                    .addressBookEntry(2)
                    .customize(e -> e.consensusTimestamp(timestamp).nodeId(3L))
                    .persist();

            // when
            final var actual = restClient
                    .get()
                    .uri("?file.id=" + fileId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(3);
        }

        @Test
        void withNodeIdEquality() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId = nodes.get(1).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=" + nodeId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(1);
            assertThat(actual.getNodes().get(0).getNodeId()).isEqualTo(nodeId);
        }

        @Test
        void withNodeIdEqualityOperator() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId = nodes.get(0).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=eq:" + nodeId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(1);
            assertThat(actual.getNodes().get(0).getNodeId()).isEqualTo(nodeId);
        }

        @Test
        void withNodeIdGreaterThan() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId = nodes.get(0).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=gt:" + nodeId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getNodes())
                    .allSatisfy(node -> assertThat(node.getNodeId()).isGreaterThan(nodeId));
        }

        @Test
        void withNodeIdGreaterThanOrEqual() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId = nodes.get(1).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=gte:" + nodeId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getNodes())
                    .allSatisfy(node -> assertThat(node.getNodeId()).isGreaterThanOrEqualTo(nodeId));
        }

        @Test
        void withNodeIdLessThan() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId = nodes.get(2).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=lt:" + nodeId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getNodes())
                    .allSatisfy(node -> assertThat(node.getNodeId()).isLessThan(nodeId));
        }

        @Test
        void withNodeIdLessThanOrEqual() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId = nodes.get(1).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=lte:" + nodeId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getNodes())
                    .allSatisfy(node -> assertThat(node.getNodeId()).isLessThanOrEqualTo(nodeId));
        }

        @Test
        void withNodeIdRange() {
            // given
            var nodes = setupNetworkNodeData();
            var minNodeId = nodes.get(0).getNodeId();
            var maxNodeId = nodes.get(1).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=gte:" + minNodeId + "&node.id=lte:" + maxNodeId)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getNodes()).allSatisfy(node -> assertThat(node.getNodeId())
                    .isGreaterThanOrEqualTo(minNodeId)
                    .isLessThanOrEqualTo(maxNodeId));
        }

        @Test
        void withNodeIdMultipleEquality() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId1 = nodes.get(0).getNodeId();
            var nodeId2 = nodes.get(2).getNodeId();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=" + nodeId1 + "&node.id=" + nodeId2)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getNodes())
                    .extracting(org.hiero.mirror.rest.model.NetworkNode::getNodeId)
                    .containsExactlyInAnyOrder(nodeId1, nodeId2);
        }

        @Test
        void withNodeIdCombinedEqualityAndRange() {
            // given
            var nodes = setupNetworkNodeData();
            var nodeId2 = nodes.get(1).getNodeId();
            var nodeId3 = nodes.get(2).getNodeId();

            // when - combining equality and range filters (AND logic)
            // node.id=2&node.id=3&node.id=gte:2 should match nodes 2 and 3 (both in equality set AND >= 2)
            final var actual = restClient
                    .get()
                    .uri("?node.id=" + nodeId2 + "&node.id=" + nodeId3 + "&node.id=gte:" + nodeId2)
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then - should return nodes matching equality AND range
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getNodes())
                    .extracting(org.hiero.mirror.rest.model.NetworkNode::getNodeId)
                    .containsExactlyInAnyOrder(nodeId2, nodeId3);
        }

        @Test
        void withLimitParameter() {
            // given
            setupNetworkNodeData();

            // when
            final var actual = restClient
                    .get()
                    .uri("?limit=2")
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(2);
            assertThat(actual.getLinks()).isNotNull();
            assertThat(actual.getLinks().getNext()).isNotNull(); // More results available
        }

        @Test
        void withOrderAsc() {
            // given
            var nodes = setupNetworkNodeData();

            // when
            final var actual = restClient
                    .get()
                    .uri("?order=asc")
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(3);
            assertThat(actual.getNodes().get(0).getNodeId())
                    .isEqualTo(nodes.get(0).getNodeId());
            assertThat(actual.getNodes().get(1).getNodeId())
                    .isEqualTo(nodes.get(1).getNodeId());
            assertThat(actual.getNodes().get(2).getNodeId())
                    .isEqualTo(nodes.get(2).getNodeId());
        }

        @Test
        void withOrderDesc() {
            // given
            var nodes = setupNetworkNodeData();

            // when
            final var actual = restClient
                    .get()
                    .uri("?order=desc")
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(3);
            assertThat(actual.getNodes().get(0).getNodeId())
                    .isEqualTo(nodes.get(2).getNodeId());
            assertThat(actual.getNodes().get(1).getNodeId())
                    .isEqualTo(nodes.get(1).getNodeId());
            assertThat(actual.getNodes().get(2).getNodeId())
                    .isEqualTo(nodes.get(0).getNodeId());
        }

        @Test
        void emptyResults() {
            // given
            setupNetworkNodeData();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=99999")
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().isEmpty();
            assertThat(actual.getLinks()).isNotNull();
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void singleResult() {
            // given
            var nodes = setupNetworkNodeData();

            // when
            final var actual = restClient
                    .get()
                    .uri("?node.id=" + nodes.get(0).getNodeId())
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(1);
            assertThat(actual.getLinks().getNext()).isNull();
        }

        @Test
        void exactlyLimitResults() {
            // given
            setupNetworkNodeData();

            // when
            final var actual = restClient
                    .get()
                    .uri("?limit=3")
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(3);
            // Using optimistic pagination: when we get exactly limit results,
            // a next link is generated even if no more data exists (matching rest module behavior)
            assertThat(actual.getLinks().getNext()).isNotNull();
        }

        @Test
        void invalidFileIdParameter() {
            // given
            setupNetworkNodeData();

            // when/then
            validateError(
                    () -> restClient.get().uri("?file.id=invalid").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid parameter: file.id");
        }

        @Test
        void invalidNodeIdParameter() {
            // given
            setupNetworkNodeData();

            // when/then
            validateError(
                    () -> restClient.get().uri("?node.id=invalid").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid parameter: node.id");
        }

        @Test
        void invalidOrderParameter() {
            // given
            setupNetworkNodeData();

            // when/then
            validateError(
                    () -> restClient.get().uri("?order=invalid").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid parameter: order");
        }

        @Test
        void invalidLimitParameter() {
            // given
            setupNetworkNodeData();

            // when/then
            validateError(
                    () -> restClient.get().uri("?limit=invalid").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid parameter: limit");
        }

        @Test
        void limitZero() {
            // given
            setupNetworkNodeData();

            // when/then
            validateError(
                    () -> restClient.get().uri("?limit=0").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid parameter: limit");
        }

        @Test
        void limitNegative() {
            // given
            setupNetworkNodeData();

            // when/then
            validateError(
                    () -> restClient.get().uri("?limit=-1").retrieve().toEntity(String.class),
                    HttpClientErrorException.BadRequest.class,
                    "Invalid parameter: limit");
        }

        @Test
        void notFoundWithInvalidFileId() {
            // given
            setupNetworkNodeData();

            // when
            final var actual = restClient
                    .get()
                    .uri("?file.id=0.0.99999")
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then - should return empty results
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().isEmpty();
        }

        @Test
        void paginationNextLink() {
            // given
            var nodes = setupNetworkNodeData();

            // when - request with limit smaller than total results
            final var actual = restClient
                    .get()
                    .uri("?limit=1")
                    .retrieve()
                    .body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().hasSize(1);
            assertThat(actual.getLinks().getNext()).isNotNull().contains("node.id");
        }

        @Test
        void allFieldsPopulated() {
            // given
            setupNetworkNodeData();

            // when
            final var actual =
                    restClient.get().uri("").retrieve().body(org.hiero.mirror.rest.model.NetworkNodesResponse.class);

            // then
            assertThat(actual).isNotNull();
            assertThat(actual.getNodes()).isNotNull().isNotEmpty();
            var firstNode = actual.getNodes().get(0);
            assertThat(firstNode.getNodeId()).isNotNull();
            assertThat(firstNode.getNodeAccountId()).isNotNull();
            assertThat(firstNode.getFileId()).isNotNull();
            assertThat(firstNode.getTimestamp()).isNotNull();
            assertThat(firstNode.getPublicKey()).isNotNull();
            assertThat(firstNode.getServiceEndpoints()).isNotNull();
        }

        private List<AddressBookEntry> setupNetworkNodeData() {
            var timestamp = domainBuilder.timestamp();
            var addressBook = domainBuilder
                    .addressBook()
                    .customize(ab -> ab.startConsensusTimestamp(timestamp))
                    .persist();

            // Create 3 network nodes with different node IDs
            var entry1 = domainBuilder
                    .addressBookEntry(2)
                    .customize(e -> e.consensusTimestamp(timestamp).nodeId(1L))
                    .persist();
            var entry2 = domainBuilder
                    .addressBookEntry(2)
                    .customize(e -> e.consensusTimestamp(timestamp).nodeId(2L))
                    .persist();
            var entry3 = domainBuilder
                    .addressBookEntry(2)
                    .customize(e -> e.consensusTimestamp(timestamp).nodeId(3L))
                    .persist();

            // Add corresponding node stake data
            domainBuilder.nodeStake().customize(ns -> ns.nodeId(1L)).persist();
            domainBuilder.nodeStake().customize(ns -> ns.nodeId(2L)).persist();
            domainBuilder.nodeStake().customize(ns -> ns.nodeId(3L)).persist();

            // Add corresponding node data
            domainBuilder.node().customize(n -> n.nodeId(1L)).persist();
            domainBuilder.node().customize(n -> n.nodeId(2L)).persist();
            domainBuilder.node().customize(n -> n.nodeId(3L)).persist();

            return List.of(entry1, entry2, entry3);
        }
    }
}
