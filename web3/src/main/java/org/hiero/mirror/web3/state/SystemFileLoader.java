// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_EXCHANGE_RATES_SYSTEM_FILE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_FILE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidFileException;
import org.hiero.mirror.web3.repository.FileDataRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Named
@NullMarked
@CustomLog
public class SystemFileLoader {

    private final EvmProperties properties;
    private final FileDataRepository fileDataRepository;
    private final SystemEntity systemEntity;
    private final FileID exchangeRateFileId;
    private final FileID feeSchedulesFileId;
    private final CacheManager exchangeRatesCacheManager;
    private final CacheManager defaultSystemFileCacheManager;

    public SystemFileLoader(
            final EvmProperties properties,
            final FileDataRepository fileDataRepository,
            final SystemEntity systemEntity,
            @Qualifier(CACHE_MANAGER_EXCHANGE_RATES_SYSTEM_FILE) final CacheManager exchangeRatesCacheManager,
            @Qualifier(CACHE_MANAGER_SYSTEM_FILE) final CacheManager defaultSystemFileCacheManager) {
        this.properties = properties;
        this.fileDataRepository = fileDataRepository;
        this.systemEntity = systemEntity;
        this.exchangeRateFileId = Utils.toFileID(systemEntity.exchangeRateFile());
        this.feeSchedulesFileId = Utils.toFileID(systemEntity.feeScheduleFile());
        this.exchangeRatesCacheManager = exchangeRatesCacheManager;
        this.defaultSystemFileCacheManager = defaultSystemFileCacheManager;
    }

    private final V0490FileSchema fileSchema = new V0490FileSchema();
    private final RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
            .maxRetries(9)
            .predicate(e -> e instanceof InvalidFileException)
            .build());

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final byte[] mockAddressBook = createMockAddressBook();

    @Getter(lazy = true)
    private final Map<FileID, SystemFile> systemFiles = loadAll();

    /**
     * Load system file by id and consensus timestamp. Uses a cache manager chosen by file id:
     * exchange rate file uses {@value org.hiero.mirror.web3.evm.config.EvmConfiguration#CACHE_MANAGER_EXCHANGE_RATES_SYSTEM_FILE}
     * (e.g. longer TTL); other system files use
     * {@value org.hiero.mirror.web3.evm.config.EvmConfiguration#CACHE_MANAGER_SYSTEM_FILE}.
     */
    public @Nullable File load(FileID fileId, long consensusTimestamp) {
        final var cacheKey = new CacheKey(fileId, consensusTimestamp);
        final var cache = getCacheForFileId(fileId);
        if (cache == null) {
            return loadFromDB(fileId, consensusTimestamp);
        }
        // Try to return the value from the cache
        var file = cache.get(cacheKey, File.class);
        if (file != null) {
            return file;
        }

        // The value was not in cache -> try to load from DB
        var result = loadFromDB(fileId, consensusTimestamp);
        if (result != null) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    private @Nullable File loadFromDB(FileID fileId, long consensusTimestamp) {
        var systemFile = getSystemFiles().get(fileId);
        if (systemFile == null) {
            return null;
        }

        return loadWithRetry(fileId, consensusTimestamp, systemFile);
    }

    public boolean isSystemFile(final FileID fileId) {
        return getSystemFiles().containsKey(fileId);
    }

    /**
     * Load file data with retry logic and parsing. This method will attempt to load and parse file data, retrying with
     * earlier versions if parsing fails.
     *
     * @param key              The FileID object representing the file
     * @param currentTimestamp The current timestamp to start loading from
     * @param systemFile       The system file containing the file data and codec for parsing
     * @return The parsed file data, or the default value if no valid data is found
     */
    private File loadWithRetry(final FileID key, final long currentTimestamp, SystemFile systemFile) {
        AtomicLong nanoSeconds = new AtomicLong(currentTimestamp);
        final var fileId = toEntityId(key).getId();
        final var attempt = new AtomicInteger(0);

        try {
            return retryTemplate.execute(() -> fileDataRepository
                    .getFileAtTimestamp(fileId, nanoSeconds.get())
                    .filter(fileData -> ArrayUtils.isNotEmpty(fileData.getFileData()))
                    .map(fileData -> {
                        try {
                            var bytes = Bytes.wrap(fileData.getFileData());
                            var codec = systemFile.codec;
                            if (codec != null) {
                                codec.parse(bytes.toReadableSequentialData());
                            }
                            return File.newBuilder().contents(bytes).fileId(key).build();
                        } catch (ParseException e) {
                            log.warn(
                                    "Failed to parse file data for fileId {} at {}, retry attempt {}. Exception: ",
                                    fileId,
                                    nanoSeconds.get(),
                                    attempt.incrementAndGet(),
                                    e);
                            nanoSeconds.set(fileData.getConsensusTimestamp() - 1);
                            throw new InvalidFileException(e);
                        }
                    })
                    .orElse(systemFile.genesisFile()));
        } catch (RetryException e) {
            return systemFile.genesisFile();
        }
    }

    private Map<FileID, SystemFile> loadAll() {
        var configuration = properties.getVersionedConfiguration();
        var addressBookMock = Bytes.wrap(getMockAddressBook());
        var files = List.of(
                new SystemFile(load(systemEntity.addressBookFile101(), addressBookMock), NodeAddressBook.PROTOBUF),
                new SystemFile(load(systemEntity.addressBookFile102(), addressBookMock), NodeAddressBook.PROTOBUF),
                new SystemFile(
                        load(systemEntity.feeScheduleFile(), fileSchema.genesisFeeSchedules(configuration)),
                        CurrentAndNextFeeSchedule.PROTOBUF),
                new SystemFile(
                        load(systemEntity.exchangeRateFile(), fileSchema.genesisExchangeRates(configuration)),
                        ExchangeRateSet.PROTOBUF),
                new SystemFile(
                        load(systemEntity.networkPropertyFile(), fileSchema.genesisNetworkProperties(configuration)),
                        null),
                new SystemFile(load(systemEntity.hapiPermissionFile(), Bytes.EMPTY), null),
                new SystemFile(
                        load(
                                systemEntity.throttleDefinitionFile(),
                                fileSchema.genesisThrottleDefinitions(configuration)),
                        ThrottleDefinitions.PROTOBUF));

        return files.stream()
                .collect(Collectors.toMap(systemFile -> systemFile.genesisFile().fileId(), Function.identity()));
    }

    private File load(EntityId entityId, Bytes contents) {
        var fileId = FileID.newBuilder()
                .shardNum(entityId.getShard())
                .realmNum(entityId.getRealm())
                .fileNum(entityId.getNum())
                .build();
        return File.newBuilder()
                .contents(contents)
                .deleted(false)
                .expirationSecond(maxExpiry())
                .fileId(fileId)
                .build();
    }

    private long maxExpiry() {
        var configuration = properties.getVersionedConfiguration();
        long maxLifetime = configuration.getConfigData(EntitiesConfig.class).maxLifetime();
        return Instant.now().getEpochSecond() + maxLifetime;
    }

    private byte[] createMockAddressBook() {
        com.hederahashgraph.api.proto.java.NodeAddressBook.Builder builder =
                com.hederahashgraph.api.proto.java.NodeAddressBook.newBuilder();
        long nodeId = 3;
        NodeAddress.Builder nodeAddressBuilder = NodeAddress.newBuilder()
                .addServiceEndpoint(ServiceEndpoint.newBuilder()
                        .setIpAddressV4(ByteString.copyFromUtf8("127.0.0." + nodeId))
                        .setPort((int) nodeId)
                        .build())
                .setNodeId(nodeId)
                .setNodeAccountId(AccountID.newBuilder()
                        // setting the shard and realm just to be safe
                        .setShardNum(CommonProperties.getInstance().getShard())
                        .setRealmNum(CommonProperties.getInstance().getRealm())
                        .setAccountNum(nodeId));
        builder.addNodeAddress(nodeAddressBuilder.build());
        return builder.build().toByteArray();
    }

    /**
     * Returns the cache for the given file id: exchange rate file and fee schedule file use the
     * exchange-rates cache manager (longer TTL). Other system files use the default manager.
     *
     * @param fileId the file id
     * @return the cache for this file id, or null if the manager has no such cache
     */
    private @Nullable Cache getCacheForFileId(FileID fileId) {
        final var isExchangeRate = fileId.equals(exchangeRateFileId);
        final var isFeeSchedule = fileId.equals(feeSchedulesFileId);
        final var useExchangeRatesManager = isExchangeRate || isFeeSchedule;
        final var manager = useExchangeRatesManager ? exchangeRatesCacheManager : defaultSystemFileCacheManager;
        return manager.getCache(CACHE_NAME);
    }

    private record SystemFile(File genesisFile, @Nullable Codec<?> codec) {}

    private record CacheKey(FileID fileId, long timestamp) {}
}
