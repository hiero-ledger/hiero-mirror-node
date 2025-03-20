// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.exception.InvalidFileException;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.mirror.web3.state.SystemFileLoader;
import com.hedera.mirror.web3.utils.Suppliers;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.throttle.ThrottleParser;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.CustomLog;
import org.springframework.retry.support.RetryTemplate;

/**
 * This class serves as a repository layer between hedera app services read only state and the Postgres database in
 * mirror-node. The file data, which is read from the database is converted to the PBJ generated format, so that it can
 * properly be utilized by the hedera app components
 */
@CustomLog
@Named
public class FileReadableKVState extends AbstractReadableKVState<FileID, File> {

    public static final String KEY = "FILES";
    private final FileDataRepository fileDataRepository;
    private final EntityRepository entityRepository;
    private final SystemFileLoader systemFileLoader;
    private final AtomicReference<File> cachedThrottles = new AtomicReference<>();
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(InvalidFileException.class)
            .build();
    private final ThrottleParser throttleParser = new ThrottleParser();

    public FileReadableKVState(
            final FileDataRepository fileDataRepository,
            final EntityRepository entityRepository,
            SystemFileLoader systemFileLoader) {
        super(KEY);
        this.fileDataRepository = fileDataRepository;
        this.entityRepository = entityRepository;
        this.systemFileLoader = systemFileLoader;
    }

    @Override
    protected File readFromDataSource(@Nonnull FileID key) {
        final var timestamp = ContractCallContext.get().getTimestamp();
        final var fileEntityId = toEntityId(key);
        final var fileId = fileEntityId.getId();

        if (fileId == 123) {
            return cachedThrottles.updateAndGet(
                    existing -> existing != null ? existing : loadThrottlesWithRetry(fileId, key));
        }

        return timestamp
                .map(t -> fileDataRepository.getFileAtTimestamp(fileId, t))
                .orElseGet(() -> fileDataRepository.getFileAtTimestamp(fileId, getCurrentTimestamp()))
                .map(fileData -> mapToFile(fileData, key, timestamp))
                .orElseGet(() -> systemFileLoader.load(key));
    }

    private File loadThrottlesWithRetry(final long fileId, final FileID key) {
        AtomicLong nanoSeconds =
                new AtomicLong(ContractCallContext.get().getTimestamp().orElse(getCurrentTimestamp()));
        AtomicReference<FileData> successfulFileData = new AtomicReference<>(null);

        retryTemplate.execute(context -> {
            Optional<FileData> fileDataOptional = fileDataRepository.getFileAtTimestamp(fileId, nanoSeconds.get());

            if (fileDataOptional.isEmpty()) {
                // Stop retrying if no file exists at the timestamp
                return Optional.empty();
            }

            FileData fileData = fileDataOptional.get();
            try {
                // Try parsing the file data
                throttleParser.parse(Bytes.wrap(fileData.getFileData()));

                // If parsing succeeds, store this fileData and stop retrying
                successfulFileData.set(fileData);
                return Optional.of(fileData);
            } catch (HandleException e) {
                log.warn(
                        "Failed to parse file data for fileId {} at {}, retry attempt {}. Exception: ",
                        fileId,
                        nanoSeconds.get(),
                        context.getRetryCount() + 1,
                        e);

                // Update timestamp to retry with an earlier version
                nanoSeconds.set(fileData.getConsensusTimestamp() - 1);
                throw new InvalidFileException(e);
            }
        });

        // If we found a valid FileData, return it
        if (successfulFileData.get() != null) {
            return mapToFile(successfulFileData.get(), key, Optional.of(nanoSeconds.get()));
        }

        // If all retries failed, return the system file loader's result
        return systemFileLoader.load(key);
    }

    private File mapToFile(final FileData fileData, final FileID key, final Optional<Long> timestamp) {
        return File.newBuilder()
                .contents(Bytes.wrap(fileData.getFileData()))
                .expirationSecond(getExpirationSeconds(toEntityId(key), timestamp))
                .fileId(key)
                .build();
    }

    private Supplier<Long> getExpirationSeconds(final EntityId entityId, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(entityId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(entityId.getId()))
                .map(AbstractEntity::getExpirationTimestamp)
                .orElse(null));
    }

    private long getCurrentTimestamp() {
        final var now = Instant.now();
        return DomainUtils.convertToNanos(now.getEpochSecond(), now.getNano());
    }
}
