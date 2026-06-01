// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.staking;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.EntityStakeRepository;
import org.springframework.transaction.support.TransactionOperations;

@CustomLog
@Named
@RequiredArgsConstructor
public class EntityStakeCalculatorImpl implements EntityStakeCalculator {

    private final EntityProperties entityProperties;
    private final EntityStakeRepository entityStakeRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final TransactionOperations transactionOperations;
    private final SystemEntity systemEntity;

    @Override
    public void calculate() {
        if (!entityProperties.getPersist().isPendingReward()) {
            return;
        }

        if (running.compareAndExchange(false, true)) {
            log.info("Skipping since the previous entity stake calculation is still running");
            return;
        }

        try {
            final var stakingRewardAccountId =
                    systemEntity.stakingRewardAccount().getId();
            final var chunkSize = Math.max(1, entityProperties.getPersist().getPendingRewardChunkSize());
            final var chunkDelay = Optional.ofNullable(
                            entityProperties.getPersist().getPendingRewardChunkDelay())
                    .orElse(Duration.ZERO);
            final boolean resume = entityProperties.getPersist().isPendingRewardChunkResume();

            while (true) {
                if (entityStakeRepository.updated(stakingRewardAccountId)) {
                    log.info("Skipping since the entity stake is up-to-date");
                    return;
                }

                final var stopwatch = Stopwatch.createStarted();
                // get last successful stake period for staking reward account
                final var lastEndStakePeriod = entityStakeRepository
                        .getEndStakePeriod(stakingRewardAccountId)
                        .orElse(0L);

                // The target staking period to process is driven by the staking reward account, and must not advance
                // until all other entities have been processed for that period.
                final var nextEndStakePeriod = entityStakeRepository.getNextEndStakePeriod(stakingRewardAccountId);
                if (nextEndStakePeriod.isEmpty()) {
                    log.info("Skipping since there is no next staking period to process");
                    return;
                }

                final long endStakePeriodToProcess = nextEndStakePeriod.get();
                long lastProcessedEntityId = resume
                        ? entityStakeRepository
                                .getLastProcessedEntityId(endStakePeriodToProcess)
                                .orElse(0L)
                        : 0L;

                log.info(
                        "Starting pending reward calculation for endStakePeriod={}, resume={}, chunkSize={}, chunkDelayMs={}, lastProcessedEntityId={}",
                        endStakePeriodToProcess,
                        resume,
                        chunkSize,
                        chunkDelay.toMillis(),
                        lastProcessedEntityId);

                final var stagingStopwatch = Stopwatch.createStarted();
                transactionOperations.executeWithoutResult(s -> {
                    entityStakeRepository.lockFromConcurrentUpdates();
                    entityStakeRepository.createEntityStateStart(stakingRewardAccountId);
                });
                log.info(
                        "Completed pending reward staging for endStakePeriod={} in {}",
                        endStakePeriodToProcess,
                        stagingStopwatch);

                while (true) {
                    final var upperBound = entityStakeRepository.getChunkUpperBoundEntityId(
                            stakingRewardAccountId, endStakePeriodToProcess, lastProcessedEntityId, chunkSize);
                    if (upperBound.isEmpty()) {
                        break;
                    }

                    final long chunkEndEntityId = upperBound.get();
                    final long chunkStartEntityId = lastProcessedEntityId;
                    final var chunkStopwatch = Stopwatch.createStarted();
                    transactionOperations.executeWithoutResult(s -> {
                        entityStakeRepository.lockFromConcurrentUpdates();
                        entityStakeRepository.updateEntityStakeChunk(
                                stakingRewardAccountId,
                                endStakePeriodToProcess,
                                chunkStartEntityId,
                                chunkEndEntityId,
                                false);
                        entityStakeRepository.saveProgress(endStakePeriodToProcess, chunkEndEntityId, false);
                    });

                    lastProcessedEntityId = chunkEndEntityId;

                    log.info(
                            "Completed pending reward chunk for endStakePeriod={}, entityIdExclusive={}, entityIdInclusive={} in {}",
                            endStakePeriodToProcess,
                            chunkStartEntityId,
                            chunkEndEntityId,
                            chunkStopwatch);

                    if (!chunkDelay.isZero()) {
                        try {
                            log.info(
                                    "Sleeping {}ms between pending reward chunks for endStakePeriod={}",
                                    chunkDelay.toMillis(),
                                    endStakePeriodToProcess);
                            Thread.sleep(chunkDelay.toMillis());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }

                // Final chunk updates the staking reward account (800) last, and marks the period as completed.
                final long finalLastProcessedEntityId = lastProcessedEntityId;
                final var finalChunkStopwatch = Stopwatch.createStarted();
                transactionOperations.executeWithoutResult(s -> {
                    entityStakeRepository.lockFromConcurrentUpdates();
                    entityStakeRepository.updateEntityStakeChunk(
                            stakingRewardAccountId, endStakePeriodToProcess, 0L, 0L, true);
                    entityStakeRepository.saveProgress(endStakePeriodToProcess, finalLastProcessedEntityId, true);
                });
                log.info(
                        "Completed pending reward final chunk (stakingRewardAccountId={}) for endStakePeriod={} in {}",
                        stakingRewardAccountId,
                        endStakePeriodToProcess,
                        finalChunkStopwatch);

                final var endStakePeriod = entityStakeRepository.getEndStakePeriod(stakingRewardAccountId);
                if (endStakePeriod
                        .filter(stakePeriod -> stakePeriod > lastEndStakePeriod)
                        .isPresent()) {
                    log.info(
                            "Completed pending reward calculation of end stake period {} in {}",
                            endStakePeriod.get(),
                            stopwatch);
                } else {
                    log.warn(
                            "Failed to calculate pending reward in {}, last end stake period is {}, and the end stake period afterwards is {}",
                            stopwatch,
                            lastEndStakePeriod,
                            endStakePeriod.orElse(null));
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to update entity stake", e);
            throw e;
        } finally {
            running.set(false);
        }
    }
}
