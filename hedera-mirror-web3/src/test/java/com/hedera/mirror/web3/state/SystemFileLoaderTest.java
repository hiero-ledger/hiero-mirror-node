// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SystemFileLoaderTest {
    private CommonProperties commonProperties;
    private SystemFileLoader systemFileLoader;

    @BeforeEach
    void setUp() {
        commonProperties = new CommonProperties();
        systemFileLoader = new SystemFileLoader(new MirrorNodeEvmProperties(commonProperties), commonProperties);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadNonSystemFile(long shard, long realm) {
        setCommonProperties(shard, realm);
        var file = systemFileLoader.load(fileId(1000));
        assertThat(file).isNull();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadAddressBook(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(101);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isEqualTo(NodeAddressBook.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadNodeDetails(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(102);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isEqualTo(NodeAddressBook.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadFeeSchedule(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(111);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var feeSchedule = CurrentAndNextFeeSchedule.PROTOBUF.parse(file.contents());
        assertThat(feeSchedule).isNotNull().isNotEqualTo(CurrentAndNextFeeSchedule.DEFAULT);
        assertThat(feeSchedule.currentFeeSchedule())
                .isNotNull()
                .extracting(FeeSchedule::transactionFeeSchedule, InstanceOfAssertFactories.LIST)
                .hasSizeGreaterThanOrEqualTo(72);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadExchangeRate(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(112);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var exchangeRateSet = ExchangeRateSet.PROTOBUF.parse(file.contents());
        assertThat(exchangeRateSet).isNotNull().isNotEqualTo(ExchangeRateSet.DEFAULT);
        assertThat(exchangeRateSet.currentRate()).isNotNull().isNotEqualTo(ExchangeRate.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadNetworkProperties(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(121);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadHapiPermissions(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(122);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadThrottleDefinitions(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(123);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(file.contents());
        assertThat(throttleDefinitions).isNotNull().isNotEqualTo(ThrottleDefinitions.DEFAULT);
        assertThat(throttleDefinitions.throttleBuckets()).hasSizeGreaterThanOrEqualTo(5);
    }

    private FileID fileId(long fileNum) {
        return FileID.newBuilder()
                .shardNum(commonProperties.getShard())
                .realmNum(commonProperties.getRealm())
                .fileNum(fileNum)
                .build();
    }

    private void assertFile(File file, FileID fileId) {
        assertThat(file)
                .isNotNull()
                .returns(fileId, File::fileId)
                .returns(false, File::deleted)
                .matches(f -> f.contents() != null)
                .matches(f -> Instant.ofEpochSecond(f.expirationSecondSupplier().get())
                        .isAfter(Instant.now().plus(92, ChronoUnit.DAYS)));
    }

    // Method that provides the test data
    public static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(1L, 2L));
    }

    private void setCommonProperties(long shard, long realm) {
        commonProperties.setShard(shard);
        commonProperties.setRealm(realm);
    }
}
