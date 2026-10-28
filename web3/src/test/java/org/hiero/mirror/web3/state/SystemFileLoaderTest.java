// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.file.FileData;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.repository.FileDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

@ExtendWith(MockitoExtension.class)
class SystemFileLoaderTest {

    private static final CommonProperties commonProperties = CommonProperties.getInstance();
    private static final long NANOS_PER_HOUR = Duration.ofHours(1).toNanos();
    private static final long HOUR = 10 * NANOS_PER_HOUR;

    private static final byte[] CORRUPT_DATA = "corrupt".getBytes();
    private static final FileData corruptFileData =
            FileData.builder().consensusTimestamp(300L).fileData(CORRUPT_DATA).build();
    final V0490FileSchema fileSchema = new V0490FileSchema();

    @Mock
    private FileDataRepository fileDataRepository;

    @Mock
    private CacheManager exchangeRatesCacheManager;

    @Mock
    private CacheManager modularizedCacheManager;

    private SystemFileLoader systemFileLoader;
    private VersionedConfiguration configuration;
    private EvmProperties evmProperties;
    private SystemEntity systemEntity;

    @BeforeEach
    void setup() {
        systemEntity = new SystemEntity(commonProperties);
        evmProperties = new EvmProperties();
        evmProperties.setMaxFileAttempts(2);
        systemFileLoader = new SystemFileLoader(
                evmProperties, fileDataRepository, systemEntity, exchangeRatesCacheManager, modularizedCacheManager);
        configuration = evmProperties.getVersionedConfiguration();
    }

    @Test
    void loadNonSystemFile() {
        var file = systemFileLoader.load(fileId(1000L), getCurrentTimestamp());
        assertThat(file).isNull();
    }

    @Test
    void loadAddressBook() throws Exception {
        var fileId = fileId(systemEntity.addressBookFile101());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isNotEqualTo(NodeAddressBook.DEFAULT);
        assertThat(nodeAddressBook.nodeAddress()).isNotNull().hasSize(1);
    }

    @Test
    void loadNodeDetails() throws Exception {
        var fileId = fileId(systemEntity.addressBookFile102());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var nodeAddressBook = NodeAddressBook.PROTOBUF.parse(file.contents());
        assertThat(nodeAddressBook).isNotNull().isNotEqualTo(NodeAddressBook.DEFAULT);
        assertThat(nodeAddressBook.nodeAddress()).isNotNull().hasSize(1);
    }

    @Test
    void loadFeeSchedule() throws Exception {
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

    @Test
    void loadSimpleFeeSchedule() throws Exception {
        var fileId = fileId(systemEntity.simpleFeeScheduleFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var simpleFeeSchedule = org.hiero.hapi.support.fees.FeeSchedule.PROTOBUF.parse(file.contents());
        assertThat(simpleFeeSchedule).isNotNull().isNotEqualTo(org.hiero.hapi.support.fees.FeeSchedule.DEFAULT);
        assertThat(simpleFeeSchedule.services()).isNotNull().hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void loadExchangeRate() throws Exception {
        var fileId = fileId(systemEntity.exchangeRateFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var exchangeRateSet = ExchangeRateSet.PROTOBUF.parse(file.contents());
        assertThat(exchangeRateSet).isNotNull().isNotEqualTo(ExchangeRateSet.DEFAULT);
        assertThat(exchangeRateSet.currentRate()).isNotNull().isNotEqualTo(ExchangeRate.DEFAULT);
    }

    @Test
    void loadNetworkProperties() throws Exception {
        var fileId = fileId(systemEntity.networkPropertyFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadHapiPermissions() throws Exception {
        var fileId = fileId(systemEntity.hapiPermissionFile());
        var file = systemFileLoader.load(fileId, getCurrentTimestamp());
        assertFile(file, fileId);
        var networkProperties = ServicesConfigurationList.PROTOBUF.parse(file.contents());
        assertThat(networkProperties).isNotNull().isEqualTo(ServicesConfigurationList.DEFAULT);
    }

    @Test
    void loadThrottleDefinitions() throws Exception {
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
    void loadSimpleFeesWithRetrySuccessfully() {
        var fileId = fileId(systemEntity.simpleFeeScheduleFile());
        var expected = getFile(fileId, fileSchema.genesisSimpleFeesSchedules(configuration));
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
        var fileId = FileID.newBuilder()
                .shardNum(commonProperties.getShard() + 1)
                .realmNum(commonProperties.getRealm() + 1)
                .fileNum(101)
                .build();
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
        verify(fileDataRepository, times(evmProperties.getMaxFileAttempts()))
                .getFileAtTimestamp(eq(entityId), anyLong());
    }

    @ParameterizedTest
    @CsvSource({
        "101, true",
        "102, true",
        "111, true",
        "112, true",
        "113, true",
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

    @Test
    void exchangeRateFilesShareTheConfiguredCacheAcrossLoaders() {
        useSystemFileCache();
        final var expected = cacheExchangeRates(HOUR, 100);
        final var otherLoader = new SystemFileLoader(
                evmProperties, fileDataRepository, systemEntity, exchangeRatesCacheManager, modularizedCacheManager);

        assertThat(otherLoader.load(fileId(systemEntity.exchangeRateFile()), HOUR + 1))
                .isSameAs(expected);
        verify(fileDataRepository)
                .getFileAtTimestamp(systemEntity.exchangeRateFile().getId(), HOUR);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1})
    void missingExchangeRatesUseTheClosestCachedFileAndRetry(final long direction) {
        useSystemFileCache();
        final var expected = cacheExchangeRates(HOUR + direction * NANOS_PER_HOUR, 100);
        cacheExchangeRates(HOUR - direction * 2 * NANOS_PER_HOUR, 200);
        cacheExchangeRates(HOUR + direction * 3 * NANOS_PER_HOUR, 300);
        final var fileId = fileId(systemEntity.exchangeRateFile());
        // A fee file at the exact requested hour must not be used as an exchange-rate fallback.
        systemFileLoader.load(fileId(systemEntity.feeScheduleFile()), HOUR);

        assertThat(systemFileLoader.load(fileId, HOUR)).isSameAs(expected);
        assertThat(cacheExchangeRates(HOUR, 400).contents()).isEqualTo(exchangeRates(400));
        verify(fileDataRepository, times(2))
                .getFileAtTimestamp(systemEntity.exchangeRateFile().getId(), HOUR);
    }

    @Test
    void equallyCloseExchangeRateFilesPreferTheEarlierHour() {
        useSystemFileCache();
        cacheExchangeRates(HOUR + NANOS_PER_HOUR, 200);
        final var previous = cacheExchangeRates(HOUR - NANOS_PER_HOUR, 100);

        assertThat(systemFileLoader.load(fileId(systemEntity.exchangeRateFile()), HOUR))
                .isSameAs(previous);
    }

    @Test
    void malformedExchangeRatesRetryThePreviousFile() {
        final var contents = Bytes.wrap(CORRUPT_DATA);
        useSystemFileCache();
        final var fileId = fileId(systemEntity.exchangeRateFile());
        final var entityId = systemEntity.exchangeRateFile().getId();
        when(fileDataRepository.getFileAtTimestamp(entityId, HOUR))
                .thenReturn(Optional.of(FileData.builder()
                        .consensusTimestamp(HOUR - 1)
                        .fileData(contents.toByteArray())
                        .build()));
        when(fileDataRepository.getFileAtTimestamp(entityId, HOUR - 2))
                .thenReturn(Optional.of(FileData.builder()
                        .fileData(exchangeRates(100).toByteArray())
                        .build()));

        assertThat(systemFileLoader.load(fileId, HOUR).contents()).isEqualTo(exchangeRates(100));
        assertThat(systemFileLoader.load(fileId, HOUR + 1).contents()).isEqualTo(exchangeRates(100));
        verify(fileDataRepository).getFileAtTimestamp(entityId, HOUR);
        verify(fileDataRepository).getFileAtTimestamp(entityId, HOUR - 2);
    }

    @Test
    void malformedExchangeRatesFallBackToTheClosestCachedFileWithoutCachingIt() {
        final var contents = Bytes.wrap(CORRUPT_DATA);
        useSystemFileCache();
        final var previous = cacheExchangeRates(HOUR - NANOS_PER_HOUR, 100);
        final var entityId = systemEntity.exchangeRateFile().getId();
        when(fileDataRepository.getFileAtTimestamp(entityId, HOUR))
                .thenReturn(Optional.of(FileData.builder()
                        .consensusTimestamp(HOUR - 1)
                        .fileData(contents.toByteArray())
                        .build()));
        when(fileDataRepository.getFileAtTimestamp(entityId, HOUR - 2))
                .thenReturn(Optional.of(FileData.builder()
                        .consensusTimestamp(HOUR - 3)
                        .fileData(contents.toByteArray())
                        .build()));

        assertThat(systemFileLoader.load(fileId(systemEntity.exchangeRateFile()), HOUR))
                .isSameAs(previous);
        assertThat(cacheExchangeRates(HOUR, 200).contents()).isEqualTo(exchangeRates(200));
        verify(fileDataRepository, times(2)).getFileAtTimestamp(entityId, HOUR);
        verify(fileDataRepository).getFileAtTimestamp(entityId, HOUR - 2);
    }

    @Test
    void missingExchangeRatesUseGenesisWithoutCachingIt() {
        useSystemFileCache();
        final var fileId = fileId(systemEntity.exchangeRateFile());

        assertThat(systemFileLoader.load(fileId, HOUR).contents())
                .isEqualTo(fileSchema.genesisExchangeRatesBytes(configuration));
        assertThat(cacheExchangeRates(HOUR, 100).contents()).isEqualTo(exchangeRates(100));
        verify(fileDataRepository, times(2))
                .getFileAtTimestamp(systemEntity.exchangeRateFile().getId(), HOUR);
    }

    @Test
    void exchangeRateLoadFailuresUseTheExistingFallback() {
        useSystemFileCache();
        final var previous = cacheExchangeRates(HOUR - NANOS_PER_HOUR, 100);
        final var failure = new IllegalStateException("database unavailable");
        when(fileDataRepository.getFileAtTimestamp(
                        systemEntity.exchangeRateFile().getId(), HOUR))
                .thenThrow(failure);

        assertThat(systemFileLoader.load(fileId(systemEntity.exchangeRateFile()), HOUR))
                .isSameAs(previous);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void emptyExchangeRateFilesUseTheDefaultWithoutRetrying(final byte[] contents) {
        final var fileId = fileId(systemEntity.exchangeRateFile());
        final var entityId = systemEntity.exchangeRateFile().getId();
        when(fileDataRepository.getFileAtTimestamp(entityId, HOUR))
                .thenReturn(Optional.of(FileData.builder()
                        .consensusTimestamp(HOUR - 1)
                        .fileData(contents)
                        .build()));

        assertThat(systemFileLoader.load(fileId, HOUR).contents())
                .isEqualTo(fileSchema.genesisExchangeRatesBytes(configuration));
        verify(fileDataRepository).getFileAtTimestamp(entityId, HOUR);
        verify(fileDataRepository, never()).getFileAtTimestamp(entityId, HOUR - 2);
    }

    @Test
    void concurrentExchangeRateMissesShareOneDatabaseLoad() throws Exception {
        useSystemFileCache();
        final var ready = new CountDownLatch(2);
        final var start = new CountDownLatch(1);
        final var loading = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        final var fileId = fileId(systemEntity.exchangeRateFile());
        when(fileDataRepository.getFileAtTimestamp(
                        systemEntity.exchangeRateFile().getId(), HOUR))
                .thenAnswer(ignored -> {
                    loading.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    return Optional.of(FileData.builder()
                            .fileData(exchangeRates(100).toByteArray())
                            .build());
                });

        try (final var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return systemFileLoader.load(fileId, HOUR);
            });
            final var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return systemFileLoader.load(fileId, HOUR + 1);
            });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isSameAs(second.get(5, TimeUnit.SECONDS));
        }
        verify(fileDataRepository)
                .getFileAtTimestamp(systemEntity.exchangeRateFile().getId(), HOUR);
    }

    private void useSystemFileCache() {
        final var cacheManager = new CaffeineCacheManager(CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(20));
        exchangeRatesCacheManager = cacheManager;
        systemFileLoader = new SystemFileLoader(
                evmProperties, fileDataRepository, systemEntity, exchangeRatesCacheManager, modularizedCacheManager);
    }

    private File cacheExchangeRates(final long timestamp, final int cents) {
        when(fileDataRepository.getFileAtTimestamp(
                        systemEntity.exchangeRateFile().getId(), timestamp))
                .thenReturn(Optional.of(FileData.builder()
                        .fileData(exchangeRates(cents).toByteArray())
                        .build()));
        return systemFileLoader.load(fileId(systemEntity.exchangeRateFile()), timestamp);
    }

    private static Bytes exchangeRates(final int cents) {
        final var current = ExchangeRate.newBuilder()
                .hbarEquiv(30_000)
                .centEquiv(cents)
                .expirationTime(new TimestampSeconds(HOUR / 1_000_000_000L + 3600))
                .build();
        return ExchangeRateSet.PROTOBUF.toBytes(ExchangeRateSet.newBuilder()
                .currentRate(current)
                .nextRate(current.copyBuilder().centEquiv(cents * 2).build())
                .build());
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
