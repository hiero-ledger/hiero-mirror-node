// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.restjava.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import jakarta.inject.Named;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.hiero.hapi.support.fees.FeeSchedule;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.restjava.RestJavaProperties;
import org.hiero.mirror.restjava.RestJavaProperties.HederaNetwork;
import org.hiero.mirror.restjava.dto.SystemFile;
import org.hiero.mirror.restjava.mapper.FeeScheduleMapper;
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
    private final QueryProperties queryProperties;
    private final SystemEntity systemEntity;
    private final FeeScheduleMapper feeScheduleMapper;
    private final RestJavaProperties restJavaProperties;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
            .delay(Duration.ofMillis(10L))
            .maxDelay(Duration.ofMillis(50L))
            .maxRetries(queryProperties.getMaxFileAttempts() - 1)
            .predicate(e -> e instanceof InvalidProtocolBufferException
                    || e instanceof ParseException
                    || e.getCause() instanceof InvalidProtocolBufferException
                    || e.getCause() instanceof ParseException)
            .build());

    @Override
    public SystemFile<ExchangeRateSet> getExchangeRate(Bound timestamp) {
        return getSystemFile(systemEntity.exchangeRateFile(), timestamp, ExchangeRateSet::parseFrom);
    }

    @Override
    public SystemFile<FeeSchedule> getFeeSchedule(Bound timestamp) {
        // For historical calls we need to call the legacy fee schedule file. Supported only for mainnet. The rest of
        // the networks default to the new simple fee schedule file.
        if (restJavaProperties.getNetwork() == HederaNetwork.MAINNET
                        && timestamp.adjustUpperBound() < HederaNetwork.MAINNET.getSimpleFeesSupportStartTimestamp()
                || restJavaProperties.getNetwork() == HederaNetwork.TESTNET
                        && timestamp.adjustUpperBound() < HederaNetwork.TESTNET.getSimpleFeesSupportStartTimestamp()) {
            final var legacyFeeSchedule =
                    getSystemFile(systemEntity.feeScheduleFile(), timestamp, CurrentAndNextFeeSchedule::parseFrom);

            final var simpleFeeSchedule = feeScheduleMapper.map(
                    legacyFeeSchedule.data(), legacyFeeSchedule.fileData().getConsensusTimestamp());

            return new SystemFile<>(legacyFeeSchedule.fileData(), simpleFeeSchedule);
        }

        return getSystemFile(
                systemEntity.simpleFeeScheduleFile(),
                timestamp,
                data -> FeeSchedule.PROTOBUF.parseStrict(Bytes.wrap(data)));
    }

    /*
     * Attempts to load and parse the system file at the given consensus timestamp. If it fails to parse, it might be an
     * incomplete or bad file. In that case, it will try earlier files until it finds one that is valid.
     */
    private <T> SystemFile<T> getSystemFile(EntityId entityId, Bound timestamp, ThrowingFunction<byte[], T> parser) {
        final var lowerBound = timestamp.getAdjustedLowerRangeValue();
        final var upperBound = new AtomicLong(timestamp.adjustUpperBound());
        final var attempt = new AtomicInteger(0);

        try {
            return getRetryTemplate()
                    .execute(() -> fileDataRepository
                            .getFileAtTimestamp(entityId.getId(), lowerBound, upperBound.get())
                            .map(fileData -> {
                                try {
                                    return new SystemFile<>(fileData, parser.apply(fileData.getFileData()));
                                } catch (Exception e) {
                                    log.warn(
                                            "Attempt {} failed to load file {} at {}, falling back to previous file: {}",
                                            attempt.incrementAndGet(),
                                            entityId,
                                            fileData.getConsensusTimestamp(),
                                            e.getMessage());
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
