// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.staking;

import com.google.common.base.Stopwatch;
import jakarta.inject.Named;
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
            var stakingRewardAccountId = systemEntity.stakingRewardAccount().getId();

            while (true) {
                if (entityStakeRepository.updated(stakingRewardAccountId)) {
                    log.info("Skipping since the entity stake is up-to-date");
                    return;
                }

                var stopwatch = Stopwatch.createStarted();
                var lastEndStakePeriod = entityStakeRepository
                        .getEndStakePeriod(stakingRewardAccountId)
                        .orElse(0L);
                transactionOperations.executeWithoutResult(s -> {
                    entityStakeRepository.lockFromConcurrentUpdates();
                    entityStakeRepository.createEntityStateStart(stakingRewardAccountId);
                    log.info("Created entity_state_start in {}", stopwatch);
                    entityStakeRepository.updateEntityStake(stakingRewardAccountId);
                });

                var endStakePeriod = entityStakeRepository.getEndStakePeriod(stakingRewardAccountId);
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
