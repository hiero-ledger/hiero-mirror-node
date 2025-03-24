// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_FILE_MODULARIZED;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_THROTTLE;
import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
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
    private final V0490FileSchema fileSchema = new V0490FileSchema();
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(InvalidFileException.class)
            .build();

    @Getter(lazy = true)
    private final Map<FileID, File> systemFiles = loadAll();

    public @Nullable File load(@Nonnull FileID key) {
        return getSystemFiles().get(key);
    }

    @Cacheable(
            cacheManager = CACHE_MANAGER_SYSTEM_FILE_MODULARIZED,
            cacheNames = CACHE_NAME_THROTTLE,
            key = "'now'",
            unless = "#result == null")
    public File loadThrottles(final FileID key, final long currentTimestamp) {
        return loadWithRetry(key, currentTimestamp, ThrottleDefinitions.PROTOBUF);
    }

    /**
     * Load file data with retry logic and parsing. This method will attempt to load and parse file data,
     * retrying with earlier versions if parsing fails.
     *
     * @param key The FileID object representing the file
     * @param currentTimestamp The current timestamp to start loading from
     * @param codec The codec to use for parsing
     * @return The parsed file data, or the default value if no valid data is found
     */
    private <T> File loadWithRetry(final FileID key, final long currentTimestamp, Codec<T> codec) {
        AtomicLong nanoSeconds = new AtomicLong(currentTimestamp);
        final var fileId = toEntityId(key).getId();

        return retryTemplate.execute(
                context -> fileDataRepository
                        .getFileAtTimestamp(fileId, nanoSeconds.get())
                        .map(fileData -> {
                            try {
                                var bytes = Bytes.wrap(fileData.getFileData());
                                codec.parse(bytes.toReadableSequentialData());
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
                        .orElse(load(key)),
                context -> load(key));
    }

    private Map<FileID, File> loadAll() {
        var configuration = properties.getVersionedConfiguration();

        var files = List.of(
                load(101, Bytes.EMPTY), // Requires a node store but these aren't used by contracts so omit
                load(102, Bytes.EMPTY),
                load(111, fileSchema.genesisFeeSchedules(configuration)),
                load(112, fileSchema.genesisExchangeRates(configuration)),
                load(121, fileSchema.genesisNetworkProperties(configuration)),
                load(122, Bytes.EMPTY), // genesisHapiPermissions() fails to load files from the classpath
                load(123, fileSchema.genesisThrottleDefinitions(configuration)));

        return files.stream().collect(Collectors.toMap(File::fileId, Function.identity()));
    }

    private File load(int fileNum, Bytes contents) {
        var fileId = FileID.newBuilder().fileNum(fileNum).build();
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
}
