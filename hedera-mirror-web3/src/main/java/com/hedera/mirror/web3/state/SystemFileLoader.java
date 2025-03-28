// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_FILE_MODULARIZED;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_MODULARIZED;
import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.CurrentAndNextFeeSchedule;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.mirror.common.CommonProperties;
import com.hedera.mirror.common.domain.entity.SystemEntity;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.exception.InvalidFileException;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.support.RetryTemplate;

@Named
@CustomLog
@RequiredArgsConstructor
public class SystemFileLoader {

    private final MirrorNodeEvmProperties properties;
    private final FileDataRepository fileDataRepository;
    private final CommonProperties commonProperties;
    private final V0490FileSchema fileSchema = new V0490FileSchema();
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(InvalidFileException.class)
            .build();

    @Getter(lazy = true)
    private final Map<FileID, SystemFile> systemFiles = loadAll();

    @Cacheable(
            cacheManager = CACHE_MANAGER_SYSTEM_FILE_MODULARIZED,
            cacheNames = CACHE_NAME_MODULARIZED,
            key = "#key",
            unless = "#result == null")
    public @Nullable File load(@Nonnull FileID key, long consensusTimestamp) {
        var systemFile = getSystemFiles().get(key);
        if (systemFile == null) {
            return null;
        }

        return loadWithRetry(key, consensusTimestamp, systemFile);
    }

    public boolean isSystemFile(final FileID key) {
        return getSystemFiles().containsKey(key);
    }

    /**
     * Load file data with retry logic and parsing. This method will attempt to load and parse file data,
     * retrying with earlier versions if parsing fails.
     *
     * @param key The FileID object representing the file
     * @param currentTimestamp The current timestamp to start loading from
     * @param systemFile The system file containing the file data and codec for parsing
     * @return The parsed file data, or the default value if no valid data is found
     */
    private File loadWithRetry(final FileID key, final long currentTimestamp, SystemFile systemFile) {
        AtomicLong nanoSeconds = new AtomicLong(currentTimestamp);
        final var fileId = toEntityId(key).getId();

        return retryTemplate.execute(
                context -> fileDataRepository
                        .getFileAtTimestamp(fileId, nanoSeconds.get())
                        .map(fileData -> {
                            try {
                                var bytes = Bytes.wrap(fileData.getFileData());
                                if (systemFile.codec != null) {
                                    systemFile.codec().parse(bytes.toReadableSequentialData());
                                }
                                return File.newBuilder()
                                        .contents(bytes)
                                        .fileId(key)
                                        .build();
                            } catch (ParseException e) {
                                log.warn(
                                        "Failed to parse file data for fileId {} at {}, retry attempt {}. Exception: ",
                                        fileId,
                                        nanoSeconds.get(),
                                        context.getRetryCount() + 1,
                                        e);
                                nanoSeconds.set(fileData.getConsensusTimestamp() - 1);
                                throw new InvalidFileException(e);
                            }
                        })
                        .orElse(systemFile.genesisFile()),
                context -> systemFile.genesisFile());
    }

    private Map<FileID, SystemFile> loadAll() {
        var configuration = properties.getVersionedConfiguration();

        var files = List.of(
                new SystemFile(load(SystemEntity.ADDRESS_BOOK_101, Bytes.EMPTY), NodeAddressBook.PROTOBUF),
                new SystemFile(load(SystemEntity.ADDRESS_BOOK_102, Bytes.EMPTY), NodeAddressBook.PROTOBUF),
                new SystemFile(
                        load(SystemEntity.FEE_SCHEDULE, fileSchema.genesisFeeSchedules(configuration)),
                        CurrentAndNextFeeSchedule.PROTOBUF),
                new SystemFile(
                        load(SystemEntity.EXCHANGE_RATE, fileSchema.genesisExchangeRates(configuration)),
                        ExchangeRateSet.PROTOBUF),
                new SystemFile(
                        load(SystemEntity.NETWORK_PROPERTY, fileSchema.genesisNetworkProperties(configuration)), null),
                new SystemFile(load(SystemEntity.HAPI_PERMISSION, Bytes.EMPTY), null),
                new SystemFile(
                        load(SystemEntity.THROTTLE_DEFINITION, fileSchema.genesisThrottleDefinitions(configuration)),
                        ThrottleDefinitions.PROTOBUF));

        return files.stream()
                .collect(Collectors.toMap(systemFile -> systemFile.genesisFile().fileId(), Function.identity()));
    }

    private File load(SystemEntity systemFile, Bytes contents) {
        var fileId = FileID.newBuilder()
                .shardNum(commonProperties.getShard())
                .realmNum(commonProperties.getRealm())
                .fileNum(systemFile.getNum())
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

    private record SystemFile(File genesisFile, Codec<?> codec) {}
}
