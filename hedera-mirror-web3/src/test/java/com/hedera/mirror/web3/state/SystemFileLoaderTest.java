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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class SystemFileLoaderTest {
    private static final CommonProperties COMMON_PROPERTIES = CommonProperties.getInstance();
    private final SystemFileLoader systemFileLoader =
            new SystemFileLoader(new MirrorNodeEvmProperties(), COMMON_PROPERTIES);

    @Test
    void loadNonSystemFile() {
        var file = systemFileLoader.load(fileId(1000));
        assertThat(file).isNull();
    }

    @Test
    void loadAddressBook() throws Exception {
        var fileId = fileId(101);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isEqualTo(NodeAddressBook.DEFAULT);
    }

    @Test
    void loadNodeDetails() throws Exception {
        var fileId = fileId(102);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isEqualTo(NodeAddressBook.DEFAULT);
    }

    @Test
    void loadFeeSchedule() throws Exception {
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

    @Test
    void loadExchangeRate() throws Exception {
        var fileId = fileId(112);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var exchangeRateSet = ExchangeRateSet.PROTOBUF.parse(file.contents());
        assertThat(exchangeRateSet).isNotNull().isNotEqualTo(ExchangeRateSet.DEFAULT);
        assertThat(exchangeRateSet.currentRate()).isNotNull().isNotEqualTo(ExchangeRate.DEFAULT);
    }

    @Test
    void loadNetworkProperties() throws Exception {
        var fileId = fileId(121);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadHapiPermissions() throws Exception {
        var fileId = fileId(122);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadThrottleDefinitions() throws Exception {
        var fileId = fileId(123);
        var file = systemFileLoader.load(fileId);
        assertFile(file, fileId);
        var throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(file.contents());
        assertThat(throttleDefinitions).isNotNull().isNotEqualTo(ThrottleDefinitions.DEFAULT);
        assertThat(throttleDefinitions.throttleBuckets()).hasSizeGreaterThanOrEqualTo(5);
    }

    private FileID fileId(long fileNum) {
        return FileID.newBuilder()
                .shardNum(COMMON_PROPERTIES.getShard())
                .realmNum(COMMON_PROPERTIES.getRealm())
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
}
