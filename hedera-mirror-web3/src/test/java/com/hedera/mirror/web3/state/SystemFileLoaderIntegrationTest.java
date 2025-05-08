// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static com.hedera.mirror.common.domain.transaction.TransactionType.FILECREATE;
import static com.hedera.mirror.common.domain.transaction.TransactionType.FILEUPDATE;
import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.FileID;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.Web3IntegrationTest;
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
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
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

    private static final ServicesConfigurationList NETWORK_PROPERTY = ServicesConfigurationList.newBuilder()
            .addNameValue(Setting.newBuilder().setName("name").build())
            .build();

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final int ADDRESS_BOOK_101 = 101;
    private static final int ADDRESS_BOOK_102 = 102;
    private static final int FEE_SCHEDULE_FILE = 111;
    private static final int EXCHANGE_RATES_FILE = 112;
    private static final int THROTTLE_DEFINITIONS_FILE = 123;

    private static Stream<Arguments> fileData() {
        return Stream.of(
                Arguments.of(ADDRESS_BOOK_101, NODE_ADDRESS_BOOK.toByteArray()),
                Arguments.of(ADDRESS_BOOK_102, NODE_ADDRESS_BOOK.toByteArray()),
                Arguments.of(FEE_SCHEDULE_FILE, FEE_SCHEDULE.toByteArray()),
                Arguments.of(EXCHANGE_RATES_FILE, EXCHANGE_RATES_SET.toByteArray()),
                Arguments.of(THROTTLE_DEFINITIONS_FILE, THROTTLE_DEFINITIONS.toByteArray()));
    }

    private static Stream<Arguments> fileNumData() {
        return Stream.of(
                Arguments.of(ADDRESS_BOOK_101),
                Arguments.of(ADDRESS_BOOK_102),
                Arguments.of(FEE_SCHEDULE_FILE),
                Arguments.of(EXCHANGE_RATES_FILE),
                Arguments.of(THROTTLE_DEFINITIONS_FILE));
    }

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

    @ParameterizedTest
    @MethodSource("fileNumData")
    void loadFileWithEmptyBytesReturnsGenesisFile(int fileNum) {
        // Setup
        final var entityId = getEntityId(fileNum);
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
    void loadFileReturnsCorrectWithEmptyAndValidFile(int fileNum, byte[] fileData) {
        // Setup
        final var entityId = getEntityId(fileNum);
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

    private EntityId getEntityId(int fileNum) {
        return switch (fileNum) {
            case ADDRESS_BOOK_101 -> systemEntity.addressBookFile101();
            case ADDRESS_BOOK_102 -> systemEntity.addressBookFile102();
            case FEE_SCHEDULE_FILE -> systemEntity.feeScheduleFile();
            case EXCHANGE_RATES_FILE -> systemEntity.exchangeRateFile();
            case THROTTLE_DEFINITIONS_FILE -> systemEntity.throttleDefinitionFile();
            default -> throw new IllegalArgumentException("Invalid fileNum: " + fileNum);
        };
    }

    private FileID fileId(EntityId fileId) {
        return FileID.newBuilder()
                .shardNum(fileId.getShard())
                .realmNum(fileId.getRealm())
                .fileNum(fileId.getNum())
                .build();
    }
}
