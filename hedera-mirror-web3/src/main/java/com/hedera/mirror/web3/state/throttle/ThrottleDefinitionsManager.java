package com.hedera.mirror.web3.state.throttle;

import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_MANAGER_SYSTEM_FILE;
import static com.hedera.mirror.web3.evm.config.EvmConfiguration.CACHE_NAME_THROTTLE;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.mirror.common.domain.file.FileData;
import com.hedera.mirror.web3.exception.InvalidFileException;
import com.hedera.mirror.web3.repository.FileDataRepository;
import com.hedera.mirror.web3.state.SystemFileLoader;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.throttle.ThrottleParser;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.support.RetryTemplate;

@Named
@CacheConfig(cacheManager = CACHE_MANAGER_SYSTEM_FILE)
@CustomLog
@RequiredArgsConstructor
public class ThrottleDefinitionsManager {

    private final FileDataRepository fileDataRepository;
    private final SystemFileLoader systemFileLoader;
    
    @Named("customThrottleParser")
    private final ThrottleParser throttleParser;
    
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(InvalidFileException.class)
            .build();

    @Cacheable(cacheNames = CACHE_NAME_THROTTLE, key = "'now'", unless = "#result == null")
    public File loadThrottles(final long fileId, final FileID key, final long currentTimestamp) {
        AtomicLong nanoSeconds = new AtomicLong(currentTimestamp);
        AtomicReference<FileData> successfulFileData = new AtomicReference<>(null);

        try {
            retryTemplate.execute(context -> {
                Optional<FileData> fileDataOptional = fileDataRepository.getFileAtTimestamp(fileId, nanoSeconds.get());

                if (fileDataOptional.isEmpty()) {
                    // Stop retrying if no file exists at the timestamp
                    return Optional.empty();
                }

                final var fileData = fileDataOptional.get();
                try {
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
        } catch (InvalidFileException e) {
            log.warn("All retry attempts failed for fileId {}: {}", fileId, e.getMessage());
            return systemFileLoader.load(key);
        }

        // If we found a valid FileData, return it
        if (successfulFileData.get() != null) {
            return File.newBuilder()
                    .contents(Bytes.wrap(successfulFileData.get().getFileData()))
                    .fileId(key)
                    .build();
        }

        // If no valid data found, use system file loader
        return systemFileLoader.load(key);
    }
} 