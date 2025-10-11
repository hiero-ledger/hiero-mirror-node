// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.repository.FileDataRepository;
import org.jspecify.annotations.NullMarked;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.function.ThrowingFunction;

@CustomLog
@Named
@NullMarked
@RequiredArgsConstructor
final class FileServiceImpl implements FileService {

    private final FileDataRepository fileDataRepository;
    private final SystemEntity systemEntity;
    private final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(10)
            .retryOn(e -> e instanceof InvalidProtocolBufferException
                    || e.getCause() instanceof InvalidProtocolBufferException)
            .build();

    @Override
    public SystemFile<ExchangeRateSet> getExchangeRate(Bound timestamp) {
        return getSystemFile(systemEntity.exchangeRateFile(), timestamp, ExchangeRateSet::parseFrom);
    }

    /*
     * Attempts to load and parse the system file at the given consensus timestamp. If it fails to parse, it might be an
     * incomplete or bad file. In that case, it will try earlier files until it finds one that is valid.
     */
    private <T extends GeneratedMessage> SystemFile<T> getSystemFile(
            EntityId entityId, Bound timestamp, ThrowingFunction<byte[], T> parser) {
        final var lowerBound = timestamp.getAdjustedLowerRangeValue();
        final var upperBound = new AtomicLong(timestamp.adjustUpperBound());

        return retryTemplate
                .execute(
                        context -> fileDataRepository
                                .getFileAtTimestamp(entityId.getId(), lowerBound, upperBound.get())
                                .map(fileData -> {
                                    try {
                                        return new SystemFile<>(fileData, parser.apply(fileData.getFileData()));
                                    } catch (Exception e) {
                                        log.warn(
                                                "Attempt {} failed to load file {} at {}, falling back to previous file.",
                                                context.getRetryCount() + 1,
                                                entityId,
                                                fileData.getConsensusTimestamp(),
                                                e);
                                        upperBound.set(fileData.getConsensusTimestamp() - 1);
                                        throw e;
                                    }
                                }),
                        c -> {
                            throw new EntityNotFoundException("File %s not found".formatted(entityId));
                        })
                .orElseThrow(() -> new EntityNotFoundException("File %s not found".formatted(entityId)));
    }
}
