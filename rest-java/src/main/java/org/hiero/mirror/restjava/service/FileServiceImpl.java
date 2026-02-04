// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.repository.FileDataRepository;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.function.ThrowingFunction;

@CustomLog
@Named
@RequiredArgsConstructor
final class FileServiceImpl implements FileService {

    private final FileDataRepository fileDataRepository;
    private final SystemEntity systemEntity;
    private final RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
            .maxRetries(9)
            .predicate(e -> e instanceof InvalidProtocolBufferException
                    || e.getCause() instanceof InvalidProtocolBufferException)
            .build());

    @Override
    public SystemFile<ExchangeRateSet> getExchangeRate(Bound timestamp) {
        return getSystemFile(systemEntity.exchangeRateFile(), timestamp, ExchangeRateSet::parseFrom);
    }

    @Override
    public SystemFile<CurrentAndNextFeeSchedule> getFeeSchedule(Bound timestamp) {
        return getSystemFile(systemEntity.feeScheduleFile(), timestamp, CurrentAndNextFeeSchedule::parseFrom);
    }

    /*
     * Attempts to load and parse the system file at the given consensus timestamp. If it fails to parse, it might be an
     * incomplete or bad file. In that case, it will try earlier files until it finds one that is valid.
     */
    private <T extends GeneratedMessage> SystemFile<T> getSystemFile(
            EntityId entityId, Bound timestamp, ThrowingFunction<byte[], T> parser) {
        final var lowerBound = timestamp.getAdjustedLowerRangeValue();
        final var upperBound = new AtomicLong(timestamp.adjustUpperBound());
        final var attempt = new AtomicInteger(0);

        try {
            return retryTemplate
                    .execute(() -> fileDataRepository
                            .getFileAtTimestamp(entityId.getId(), lowerBound, upperBound.get())
                            .map(fileData -> {
                                try {
                                    return new SystemFile<>(fileData, parser.apply(fileData.getFileData()));
                                } catch (Exception e) {
                                    log.warn(
                                            "Attempt {} failed to load file {} at {}, falling back to previous file.",
                                            attempt.incrementAndGet(),
                                            entityId,
                                            fileData.getConsensusTimestamp(),
                                            e);
                                    upperBound.set(fileData.getConsensusTimestamp() - 1);
                                    throw e;
                                }
                            }))
                    .orElseThrow(() -> new EntityNotFoundException("File %s not found".formatted(entityId)));
        } catch (RetryException e) {
            throw new EntityNotFoundException("File %s not found".formatted(entityId), e);
        }
    }
}
