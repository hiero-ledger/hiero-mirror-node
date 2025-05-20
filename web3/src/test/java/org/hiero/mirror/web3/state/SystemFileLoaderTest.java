// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.hedera.mirror.common.domain.SystemEntity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import org.hiero.mirror.web3.repository.FileDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemFileLoaderTest {

    private static final byte[] CORRUPT_DATA = "corrupt".getBytes();
    private static final FileData corruptFileData =
            FileData.builder().consensusTimestamp(300L).fileData(CORRUPT_DATA).build();
    final V0490FileSchema fileSchema = new V0490FileSchema();

    @Mock
    private FileDataRepository fileDataRepository;

    private SystemFileLoader systemFileLoader;
    private CommonProperties commonProperties;
    private VersionedConfiguration configuration;
    private SystemEntity systemEntity;

    // Method that provides the test data
    public static Stream<Arguments> shardAndRealmData() {
        return Stream.of(Arguments.of(0L, 0L), Arguments.of(0L, 2L), Arguments.of(1L, 0L), Arguments.of(1L, 2L));
    }

    @BeforeEach
    void setup() {
        commonProperties = new CommonProperties();
        systemEntity = new SystemEntity(commonProperties);
        final var mirrorNodeEvmProperties = new MirrorNodeEvmProperties(commonProperties, systemEntity);
        systemFileLoader = new SystemFileLoader(mirrorNodeEvmProperties, fileDataRepository, systemEntity);
        configuration = mirrorNodeEvmProperties.getVersionedConfiguration();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadNonSystemFile(long shard, long realm) {
        setCommonProperties(shard, realm);
        var file = systemFileLoader.load(fileId(1000), getCurrentTimestamp());
        assertThat(file).isNull();
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadAddressBook(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(systemEntity.addressBookFile101());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isNotEqualTo(NodeAddressBook.DEFAULT);
        assertThat(nodeAddressBook.nodeAddress()).isNotNull().hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadNodeDetails(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(systemEntity.addressBookFile102());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isNotEqualTo(NodeAddressBook.DEFAULT);
        assertThat(nodeAddressBook.nodeAddress()).isNotNull().hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadFeeSchedule(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(systemEntity.feeScheduleFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
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
        var fileId = fileId(systemEntity.exchangeRateFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var exchangeRateSet = ExchangeRateSet.PROTOBUF.parse(file.contents());
        assertThat(exchangeRateSet).isNotNull().isNotEqualTo(ExchangeRateSet.DEFAULT);
        assertThat(exchangeRateSet.currentRate()).isNotNull().isNotEqualTo(ExchangeRate.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadNetworkProperties(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(systemEntity.networkPropertyFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadHapiPermissions(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(systemEntity.hapiPermissionFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @ParameterizedTest
    @MethodSource("shardAndRealmData")
    void loadThrottleDefinitions(long shard, long realm) throws Exception {
        setCommonProperties(shard, realm);
        var fileId = fileId(systemEntity.throttleDefinitionFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var throttleDefinitions = ThrottleDefinitions.PROTOBUF.parse(file.contents());
        assertThat(throttleDefinitions).isNotNull().isNotEqualTo(ThrottleDefinitions.DEFAULT);
        assertThat(throttleDefinitions.throttleBuckets()).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    void loadThrottlesWithRetryAndCorruptData() {
        var fileId = fileId(systemEntity.throttleDefinitionFile());
        var expected = getFile(fileId, fileSchema.genesisThrottleDefinitions(configuration));
        var entityId = toEntityId(fileId).getId();
        assertFile(expected, fileId);
        long currentNanos = 350L;
        when(fileDataRepository.getFileAtTimestamp(entityId, currentNanos))
                .thenReturn(Optional.of(corruptFileData))
                .thenReturn(Optional.of(FileData.builder()
                        .fileData(expected.contents().toByteArray())
                        .build()));

        final var actual = systemFileLoader.load(fileId, currentNanos);

        assertThat(actual)
                .isNotNull()
                .returns(expected.contents(), File::contents)
                .returns(fileId, File::fileId);
        verify(fileDataRepository, times(2)).getFileAtTimestamp(eq(entityId), anyLong());
    }

    @Test
    void loadThrottlesWithRetryCorruptDataResolveToSystemFile() {
        var fileId = fileId(systemEntity.throttleDefinitionFile());
        var expected = getFile(fileId, fileSchema.genesisThrottleDefinitions(configuration));
        var entityId = toEntityId(fileId).getId();
        assertFile(expected, fileId);
        when(fileDataRepository.getFileAtTimestamp(eq(entityId), anyLong())).thenReturn(Optional.of(corruptFileData));

        final var actual = systemFileLoader.load(fileId, 350L);

        assertThat(actual)
                .isNotNull()
                .returns(expected.contents(), File::contents)
                .returns(fileId, File::fileId);
        verify(fileDataRepository, times(10)).getFileAtTimestamp(eq(entityId), anyLong());
    }

    @Test
    void loadThrottlesWithRetrySuccessfully() {
        var fileId = fileId(systemEntity.throttleDefinitionFile());
        var expected = getFile(fileId, fileSchema.genesisThrottleDefinitions(configuration));
        var entityId = toEntityId(fileId).getId();
        assertFile(expected, fileId);
        when(fileDataRepository.getFileAtTimestamp(eq(entityId), anyLong()))
                .thenReturn(Optional.of(FileData.builder()
                        .fileData(expected.contents().toByteArray())
                        .build()));

        final var actual = systemFileLoader.load(fileId, 250L);

        assertThat(actual)
                .isNotNull()
                .returns(expected.contents(), File::contents)
                .returns(fileId, File::fileId);
        verify(fileDataRepository, times(1)).getFileAtTimestamp(eq(entityId), anyLong());
    }

    @Test
    void loadFeesWithRetrySuccessfully() {
        var fileId = fileId(systemEntity.feeScheduleFile());
        var expected = getFile(fileId, fileSchema.genesisFeeSchedules(configuration));
        var entityId = toEntityId(fileId).getId();
        assertFile(expected, fileId);
        when(fileDataRepository.getFileAtTimestamp(eq(entityId), anyLong()))
                .thenReturn(Optional.of(FileData.builder()
                        .fileData(expected.contents().toByteArray())
                        .build()));

        final var actual = systemFileLoader.load(fileId, 250L);

        assertThat(actual)
                .isNotNull()
                .returns(expected.contents(), File::contents)
                .returns(fileId, File::fileId);
        verify(fileDataRepository, times(1)).getFileAtTimestamp(eq(entityId), anyLong());
    }

    @Test
    void loadWithIncorrectShardAndRealm() {
        var fileId = FileID.newBuilder().shardNum(1).realmNum(1).fileNum(101).build();
        final var actual = systemFileLoader.load(fileId, 250L);
        assertThat(actual).isNull();
    }

    @Test
    void loadWithNonSystemFileID() {
        var fileId = fileId(1);
        final var actual = systemFileLoader.load(fileId, 250L);
        assertThat(actual).isNull();
    }

    @Test
    void loadThrottlesWithRetryNoData() {
        var fileId = fileId(systemEntity.throttleDefinitionFile());
        var expected = getFile(fileId, fileSchema.genesisThrottleDefinitions(configuration));
        var entityId = toEntityId(fileId).getId();
        assertFile(expected, fileId);
        when(fileDataRepository.getFileAtTimestamp(eq(entityId), anyLong())).thenReturn(Optional.empty());

        final var actual = systemFileLoader.load(fileId, 100L);

        assertThat(actual)
                .isNotNull()
                .returns(expected.contents(), File::contents)
                .returns(fileId, File::fileId);
        verify(fileDataRepository, times(1)).getFileAtTimestamp(eq(entityId), anyLong());
    }

    @Test
    void loadThrottlesWithRetryAllRetriesFailing() {
        var fileId = fileId(systemEntity.throttleDefinitionFile());
        var expected = getFile(fileId, fileSchema.genesisThrottleDefinitions(configuration));
        var entityId = toEntityId(fileId).getId();
        assertFile(expected, fileId);
        when(fileDataRepository.getFileAtTimestamp(eq(entityId), anyLong())).thenReturn(Optional.of(corruptFileData));

        final var actual = systemFileLoader.load(fileId, 350L);

        assertThat(actual)
                .isNotNull()
                .returns(expected.contents(), File::contents)
                .returns(fileId, File::fileId);
        verify(fileDataRepository, times(10)).getFileAtTimestamp(eq(entityId), anyLong());
    }

    @ParameterizedTest
    @CsvSource({
        "101, true",
        "102, true",
        "111, true",
        "112, true",
        "121, true",
        "122, true",
        "123, true",
        "1000, false",
        "2000, false",
        "9999, false",
        "0, false",
        "-1, false"
    })
    void isSystemFileCheck(long fileNum, boolean expectedResult) {
        assertThat(systemFileLoader.isSystemFile(fileId(fileNum))).isEqualTo(expectedResult);
    }

    private FileID fileId(EntityId fileId) {
        return FileID.newBuilder()
                .shardNum(fileId.getShard())
                .realmNum(fileId.getRealm())
                .fileNum(fileId.getNum())
                .build();
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

    private void setCommonProperties(long shard, long realm) {
        commonProperties.setShard(shard);
        commonProperties.setRealm(realm);
    }

    private long getCurrentTimestamp() {
        final var now = Instant.now();
        return DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano());
    }

    private File getFile(final FileID fileId, final Bytes contents) {
        return File.newBuilder()
                .contents(contents)
                .deleted(false)
                .fileId(fileId)
                .expirationSecond(Instant.now().getEpochSecond()
                        + configuration.getConfigData(EntitiesConfig.class).maxLifetime())
                .build();
    }
}
