// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static org.hiero.mirror.common.domain.transaction.TransactionType.FILEUPDATE;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.FileID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.Web3IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SystemFileLoaderIntegrationTest extends Web3IntegrationTest {
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();

    private static final ExchangeRateSet EXCHANGE_RATES_SET = ExchangeRateSet.newBuilder()
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

    private static final ExchangeRateSet EXCHANGE_RATES_SET_2 = ExchangeRateSet.newBuilder()
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
            .build();

    private static final long FIRST_NODE = 3L;
    private static final long SECOND_NODE = 4L;
    private static final NodeAddressBook NODE_ADDRESS_BOOK = NodeAddressBook.newBuilder()
            .addNodeAddress(NodeAddress.newBuilder()
                    .addServiceEndpoint(ServiceEndpoint.newBuilder()
                            .setIpAddressV4(ByteString.copyFromUtf8("127.0.0." + FIRST_NODE))
                            .setPort((int) FIRST_NODE)
                            .build())
                    .setNodeId(FIRST_NODE)
                    .setNodeAccountId(AccountID.newBuilder()
                            .setShardNum(COMMON_PROPERTIES.getShard())
                            .setRealmNum(COMMON_PROPERTIES.getRealm())
                            .setAccountNum(FIRST_NODE))
                    .build())
            .addNodeAddress(NodeAddress.newBuilder()
                    .addServiceEndpoint(ServiceEndpoint.newBuilder()
                            .setIpAddressV4(ByteString.copyFromUtf8("127.0.0." + SECOND_NODE))
                            .setPort((int) SECOND_NODE)
                            .build())
                    .setNodeId(SECOND_NODE)
                    .setNodeAccountId(AccountID.newBuilder()
                            .setShardNum(COMMON_PROPERTIES.getShard())
                            .setRealmNum(COMMON_PROPERTIES.getRealm())
                            .setAccountNum(SECOND_NODE))
                    .build())
            .build();

    private static final com.hederahashgraph.api.proto.java.ThrottleDefinitions THROTTLE_DEFINITIONS =
            com.hederahashgraph.api.proto.java.ThrottleDefinitions.newBuilder()
                    .addThrottleBuckets(com.hederahashgraph.api.proto.java.ThrottleBucket.newBuilder()
                            .setName("throttleBucket1")
                            .build())
                    .build();

    private static final CurrentAndNextFeeSchedule FEE_SCHEDULE = CurrentAndNextFeeSchedule.newBuilder()
            .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                    .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                            .addFees(FeeData.newBuilder()
                                    .setNodedata(
                                            FeeComponents.newBuilder().setBpr(1).build())
                                    .build())
                            .build())
                    .build())
            .build();

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final SystemFileLoader systemFileLoader;

    @Test
    void loadCachingBehavior() {
        // Setup
        final var fileId = fileId(systemEntity.exchangeRateFile());
        final var entityId = toEntityId(fileId);

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILECREATE.getProtoId())
                        .fileData(EXCHANGE_RATES_SET.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(200L))
                .persist();

        // First load - should get from DB
        final var firstLoad = systemFileLoader.load(fileId, 350L);
        assertThat(firstLoad).isNotNull();
        assertThat(firstLoad.contents()).isEqualTo(Bytes.wrap(EXCHANGE_RATES_SET.toByteArray()));

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEUPDATE.getProtoId())
                        .fileData(EXCHANGE_RATES_SET_2.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(300L))
                .persist();

        // Second load - should get from cache
        final var secondLoad = systemFileLoader.load(fileId, 350L);
        assertThat(secondLoad).isNotNull();
        assertThat(secondLoad.contents()).isEqualTo(Bytes.wrap(EXCHANGE_RATES_SET.toByteArray()));
    }

    @Test
    void loadWithDifferentTimestampsReturnsDifferentCachedResults() {
        final var fileId = fileId(systemEntity.exchangeRateFile());
        final var entityId = toEntityId(fileId);

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILECREATE.getProtoId())
                        .fileData(EXCHANGE_RATES_SET.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(100L))
                .persist();

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEUPDATE.getProtoId())
                        .fileData(EXCHANGE_RATES_SET_2.toByteArray())
                        .entityId(entityId)
                        .consensusTimestamp(200L))
                .persist();

        final var resultAtTimestamp1 = systemFileLoader.loadExchangeRates(150L);
        final var resultAtTimestamp2 = systemFileLoader.loadExchangeRates(350L);

        assertThat(resultAtTimestamp1).isNotNull();
        assertThat(resultAtTimestamp1.contents()).isEqualTo(Bytes.wrap(EXCHANGE_RATES_SET.toByteArray()));

        assertThat(resultAtTimestamp2).isNotNull();
        assertThat(resultAtTimestamp2.contents()).isEqualTo(Bytes.wrap(EXCHANGE_RATES_SET_2.toByteArray()));

        // Verify the cache returns the same distinct results on subsequent calls
        final var cachedResult1 = systemFileLoader.loadExchangeRates(150L);
        final var cachedResult2 = systemFileLoader.loadExchangeRates(350L);

        assertThat(cachedResult1.contents()).isEqualTo(resultAtTimestamp1.contents());
        assertThat(cachedResult2.contents()).isEqualTo(resultAtTimestamp2.contents());
        assertThat(cachedResult1.contents()).isNotEqualTo(cachedResult2.contents());
    }

    @ParameterizedTest
    @MethodSource("fileNumData")
    void loadFileWithEmptyBytesReturnsGenesisFile(EntityId entityId) {
        // Setup
        final var fileId = fileId(entityId);

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILECREATE.getProtoId())
                        .fileData(EMPTY_BYTES)
                        .entityId(entityId)
                        .consensusTimestamp(200L))
                .persist();

        final var actualFile = systemFileLoader.load(fileId, 350L);
        assertThat(actualFile).isNotNull();
        assertThat(actualFile.contents()).isNotNull();
        assertThat(actualFile.contents().length()).isGreaterThan(0);
    }

    @ParameterizedTest
    @MethodSource("fileData")
    void loadFileReturnsCorrectWithEmptyAndValidFile(EntityId entityId, byte[] fileData) {
        // Setup
        final var fileId = fileId(entityId);

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILECREATE.getProtoId())
                        .fileData(EMPTY_BYTES)
                        .entityId(entityId)
                        .consensusTimestamp(100L))
                .persist();

        domainBuilder
                .fileData()
                .customize(f -> f.transactionType(FILEUPDATE.getProtoId())
                        .fileData(fileData)
                        .entityId(entityId)
                        .consensusTimestamp(200L))
                .persist();

        final var actualFile = systemFileLoader.load(fileId, 350L);
        assertThat(actualFile).isNotNull();
        assertThat(actualFile.contents()).isEqualTo(Bytes.wrap(fileData));
    }

    private Stream<Arguments> fileData() {
        return Stream.of(
                Arguments.of(systemEntity.addressBookFile101(), NODE_ADDRESS_BOOK.toByteArray()),
                Arguments.of(systemEntity.addressBookFile102(), NODE_ADDRESS_BOOK.toByteArray()),
                Arguments.of(systemEntity.feeScheduleFile(), FEE_SCHEDULE.toByteArray()),
                Arguments.of(systemEntity.exchangeRateFile(), EXCHANGE_RATES_SET.toByteArray()),
                Arguments.of(systemEntity.throttleDefinitionFile(), THROTTLE_DEFINITIONS.toByteArray()));
    }

    private Stream<Arguments> fileNumData() {
        return fileData().map(args -> Arguments.of(args.get()[0]));
    }

    private FileID fileId(EntityId fileId) {
        return FileID.newBuilder()
                .shardNum(fileId.getShard())
                .realmNum(fileId.getRealm())
                .fileNum(fileId.getNum())
                .build();
    }
}
