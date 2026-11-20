// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_EXCHANGE_RATES_SYSTEM_FILE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_FILE;
import static org.hiero.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
import org.hiero.hapi.support.fees.Extra;
import org.hiero.hapi.support.fees.ExtraFeeDefinition;
import org.hiero.mirror.common.CommonProperties;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.web3.evm.properties.EvmProperties;
import org.hiero.mirror.web3.exception.InvalidFileException;
import org.hiero.mirror.web3.repository.FileDataRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Named
@NullMarked
@CustomLog
public final class SystemFileLoader {

    private static final long FEE_DIVISOR_FACTOR = 1000L;
    private static final long MAINNET_SIMPLE_FEES_TIMESTAMP = 1779296400389248896L;
    private static final long TESTNET_SIMPLE_FEES_TIMESTAMP = 1777482002529719510L;
    private static final long NANOS_PER_HOUR = 3600L * DomainUtils.NANOS_PER_SECOND;
    private static final Set<HederaFunctionality> GAS_PRICED_FUNCTIONALITIES = Set.of(
            HederaFunctionality.CONTRACT_CALL,
            HederaFunctionality.CONTRACT_CREATE,
            HederaFunctionality.ETHEREUM_TRANSACTION);

    private final EvmProperties properties;
    private final FileDataRepository fileDataRepository;
    private final SystemEntity systemEntity;
    private final FileID exchangeRateFileId;
    private final FileID feeSchedulesFileId;
    private final FileID simpleFeeSchedulesFileId;
    private final CacheManager exchangeRatesCacheManager;
    private final CacheManager defaultSystemFileCacheManager;
    private final V0490FileSchema fileSchema = new V0490FileSchema();
    private final File genesisNetworkProperties;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
            .delay(Duration.ofMillis(10L))
            .maxDelay(Duration.ofMillis(50L))
            .maxRetries(properties.getMaxFileAttempts() - 1)
            .predicate(e -> e instanceof InvalidFileException)
            .build());

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Map<FileID, SystemFile> systemFiles = loadAll();

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
        this.simpleFeeSchedulesFileId = Utils.toFileID(systemEntity.simpleFeeScheduleFile());
        this.exchangeRatesCacheManager = exchangeRatesCacheManager;
        this.defaultSystemFileCacheManager = defaultSystemFileCacheManager;
        this.genesisNetworkProperties = load(
                systemEntity.networkPropertyFile(),
                fileSchema.genesisNetworkProperties(properties.getVersionedConfiguration()));
    }

    /**
     * Load system file by id and consensus timestamp.
     */
    public @Nullable File load(FileID fileId, long consensusTimestamp) {
        // Skip database for network properties so that CN props can't override MN props and cause us to break.
        if (genesisNetworkProperties.fileId().equals(fileId)) {
            return genesisNetworkProperties;
        }

        var cacheManager = defaultSystemFileCacheManager;
        if (fileId.equals(exchangeRateFileId)
                || fileId.equals(feeSchedulesFileId)
                || fileId.equals(simpleFeeSchedulesFileId)) {
            cacheManager = exchangeRatesCacheManager;
            consensusTimestamp = feeFileCacheTimestamp(fileId, consensusTimestamp);
        }

        final var cacheKey = new CacheKey(fileId, consensusTimestamp);
        log.debug("Looking up {}", cacheKey);
        final var cache = cacheManager.getCache(CACHE_NAME);
        final long timestamp = consensusTimestamp;

        // Consensus nodes only read the simple fee file. Before simple fees were enabled, present the legacy
        // schedule converted into that format.
        final boolean shouldUseLegacyFeesFile =
                fileId.equals(simpleFeeSchedulesFileId) && consensusTimestamp < simpleFeesTimestamp();
        if (cache == null) {
            return shouldUseLegacyFeesFile ? convertLegacyFeeSchedule(timestamp) : loadFromDB(fileId, timestamp);
        }
        if (shouldUseLegacyFeesFile) {
            return cache.get(cacheKey, () -> convertLegacyFeeSchedule(timestamp));
        }
        return cache.get(cacheKey, () -> loadFromDB(fileId, cacheKey.timestamp()));
    }

    private @Nullable File loadFromDB(FileID fileId, long consensusTimestamp) {
        var systemFile = getSystemFiles().get(fileId);
        if (systemFile == null) {
            return null;
        }

        return loadWithRetry(fileId, consensusTimestamp, systemFile);
    }

    private File convertLegacyFeeSchedule(long consensusTimestamp) {
        final var legacy = load(feeSchedulesFileId, consensusTimestamp);
        if (legacy != null && legacy.contents() != null) {
            final var converted = toSimpleFeeSchedule(legacy.contents(), consensusTimestamp);
            if (converted != null) {
                return File.newBuilder()
                        .contents(converted)
                        .fileId(simpleFeeSchedulesFileId)
                        .build();
            }
        }

        final var simpleFeeSchedule = loadFromDB(simpleFeeSchedulesFileId, consensusTimestamp);
        if (simpleFeeSchedule != null) {
            return simpleFeeSchedule;
        }
        final var registered = getSystemFiles().get(simpleFeeSchedulesFileId);
        if (registered == null) {
            throw new IllegalStateException("Simple fee schedule file is not registered");
        }
        return registered.genesisFile();
    }

    private @Nullable Bytes toSimpleFeeSchedule(Bytes legacyBytes, long consensusTimestamp) {
        try {
            final var legacy = CurrentAndNextFeeSchedule.PROTOBUF.parse(legacyBytes);
            var schedule = legacy.currentFeeScheduleOrElse(FeeSchedule.DEFAULT);
            final long expirySeconds =
                    schedule.hasExpiryTime() ? schedule.expiryTimeOrThrow().seconds() : 0L;
            if (consensusTimestamp > expirySeconds * DomainUtils.NANOS_PER_SECOND && legacy.hasNextFeeSchedule()) {
                schedule = legacy.nextFeeScheduleOrThrow();
            }

            for (final var transactionFee : schedule.transactionFeeSchedule()) {
                final var functionality = transactionFee.hederaFunctionality();
                if (functionality == null
                        || !GAS_PRICED_FUNCTIONALITIES.contains(functionality)
                        || transactionFee.fees().isEmpty()) {
                    continue;
                }
                final var feeData = transactionFee.fees().getFirst();
                // The consensus node prices one unit of gas from the service component only.
                // Node and network gas values are not part of that price.
                if (!feeData.hasServicedata()) {
                    continue;
                }
                final long gasTinycents = feeData.servicedataOrThrow().gas() / FEE_DIVISOR_FACTOR;
                final var simple = org.hiero.hapi.support.fees.FeeSchedule.newBuilder()
                        .extras(ExtraFeeDefinition.newBuilder()
                                .name(Extra.GAS)
                                .fee(gasTinycents)
                                .build())
                        .build();
                return org.hiero.hapi.support.fees.FeeSchedule.PROTOBUF.toBytes(simple);
            }
        } catch (ParseException ex) {
            log.warn("Unable to convert legacy fee schedule at {}", consensusTimestamp, ex);
        }
        return null;
    }

    private long simpleFeesTimestamp() {
        return switch (properties.getNetwork()) {
            case MAINNET -> MAINNET_SIMPLE_FEES_TIMESTAMP;
            case TESTNET -> TESTNET_SIMPLE_FEES_TIMESTAMP;
            case PREVIEWNET, OTHER -> 0L;
        };
    }

    /**
     * Fee files are cached by the hour. A request before the simple-fees switchover and one after it can fall in the
     * same hour, so that hour uses two cache keys.
     */
    private long feeFileCacheTimestamp(FileID fileId, long consensusTimestamp) {
        final long hour = roundDownToHour(consensusTimestamp);
        if (!fileId.equals(simpleFeeSchedulesFileId)) {
            return hour;
        }
        final long switchover = simpleFeesTimestamp();
        if (switchover == 0L || hour != roundDownToHour(switchover)) {
            return hour;
        }
        return consensusTimestamp < switchover ? hour : switchover;
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
        final var nanoSeconds = new AtomicLong(currentTimestamp);
        final var fileId = toEntityId(key).getId();
        final var attempt = new AtomicInteger(0);

        try {
            return getRetryTemplate()
                    .execute(() -> fileDataRepository
                            .getFileAtTimestamp(fileId, nanoSeconds.get())
                            .filter(fileData -> ArrayUtils.isNotEmpty(fileData.getFileData()))
                            .map(fileData -> {
                                try {
                                    var bytes = Bytes.wrap(fileData.getFileData());
                                    var codec = systemFile.codec;
                                    if (codec != null) {
                                        codec.parse(bytes.toReadableSequentialData());
                                    }
                                    return File.newBuilder()
                                            .contents(bytes)
                                            .fileId(key)
                                            .build();
                                } catch (ParseException e) {
                                    log.warn(
                                            "Attempt {} failed to load file {} at {}, falling back to previous file: {}",
                                            attempt.incrementAndGet(),
                                            fileId,
                                            nanoSeconds.get(),
                                            e.getMessage());
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
        var addressBookMock = Bytes.wrap(createMockAddressBook());
        var files = List.of(
                new SystemFile(load(systemEntity.addressBookFile101(), addressBookMock), NodeAddressBook.PROTOBUF),
                new SystemFile(load(systemEntity.addressBookFile102(), addressBookMock), NodeAddressBook.PROTOBUF),
                new SystemFile(
                        load(systemEntity.feeScheduleFile(), GenesisFeeSchedules.protobufBytes()),
                        CurrentAndNextFeeSchedule.PROTOBUF),
                new SystemFile(
                        load(
                                systemEntity.simpleFeeScheduleFile(),
                                fileSchema.genesisSimpleFeesSchedules(configuration)),
                        FeeSchedule.PROTOBUF),
                new SystemFile(
                        load(systemEntity.exchangeRateFile(), fileSchema.genesisExchangeRatesBytes(configuration)),
                        ExchangeRateSet.PROTOBUF),
                new SystemFile(genesisNetworkProperties, null),
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
        final var builder = com.hederahashgraph.api.proto.java.NodeAddressBook.newBuilder();
        long nodeId = 3;
        final var nodeAddressBuilder = NodeAddress.newBuilder()
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
     * Rounds the given consensus timestamp (nanoseconds) down to the start of the hour (e.g. 00:00, 01:00, 02:00).
     */
    private long roundDownToHour(long consensusTimestampNanos) {
        return (consensusTimestampNanos / NANOS_PER_HOUR) * NANOS_PER_HOUR;
    }

    private record SystemFile(File genesisFile, @Nullable Codec<?> codec) {}

    private record CacheKey(FileID fileId, long timestamp) {

        @Override
        public String toString() {
            final var entityId = EntityId.of(fileId.shardNum(), fileId.realmNum(), fileId.fileNum());
            return "FileId=" + entityId + ", timestamp=" + Instant.ofEpochSecond(0L, timestamp);
        }
    }
}
