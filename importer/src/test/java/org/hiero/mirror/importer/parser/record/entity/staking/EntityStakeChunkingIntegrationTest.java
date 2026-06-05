// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.parser.record.entity.staking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hiero.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;

import com.google.common.collect.Range;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.awaitility.Durations;
import org.hiero.mirror.common.domain.balance.AccountBalance.Id;
import org.hiero.mirror.common.domain.entity.EntityId;
import org.hiero.mirror.common.domain.entity.EntityStake;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.parser.record.entity.EntityProperties;
import org.hiero.mirror.importer.repository.EntityStakeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Integration tests for chunked pending reward calculation: full-period snapshot staging and
 * per-chunk entity stake updates via {@link EntityStakeCalculator}.
 */
@RequiredArgsConstructor
class EntityStakeChunkingIntegrationTest extends ImporterIntegrationTest {

    private static final long SPLIT_ID = 150L;
    private static final long STAKER_ID = 100L;
    private static final long TARGET_ID = 200L;

    private final EntityProperties entityProperties;
    private final EntityStakeCalculator entityStakeCalculator;
    private final EntityStakeRepository entityStakeRepository;
    private final TransactionOperations transactionOperations;

    private long stakingRewardAccountId;

    @BeforeEach
    void setup() {
        stakingRewardAccountId = systemEntity.stakingRewardAccount().getId();
        entityProperties.getPersist().setPendingReward(true);
        entityProperties.getPersist().setPendingRewardChunkDelay(Duration.ZERO);
        entityProperties.getPersist().setPendingRewardChunkResume(true);
    }

    @AfterEach
    void cleanup() {
        entityProperties.getPersist().setPendingReward(false);
        entityProperties.getPersist().setPendingRewardChunkSize(5_000);
    }

    @Test
    void calculateWithSmallChunksMatchesOneShot() {
        final long epochDay = setupCrossChunkProxyScenario();

        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId, epochDay);
            entityStakeRepository.updateEntityStake(stakingRewardAccountId);
        });
        final List<EntityStake> expected = StreamSupport.stream(
                        entityStakeRepository.findAll().spliterator(), false)
                .toList();

        resetStakeTables();

        entityProperties.getPersist().setPendingRewardChunkSize(1);
        entityStakeCalculator.calculate();
        await().atMost(Durations.TEN_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(entityStakeRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparator()
                        .containsExactlyInAnyOrderElementsOf(expected));
    }

    @Test
    void calculateResumesFromPartialProgress() {
        final long epochDay = setupCrossChunkProxyScenario();

        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId, epochDay);
            entityStakeRepository.updateEntityStake(stakingRewardAccountId);
        });
        final List<EntityStake> expected = StreamSupport.stream(
                        entityStakeRepository.findAll().spliterator(), false)
                .toList();

        resetStakeTables();

        // Simulate an interrupted run after the first chunk boundary without completing the period.
        transactionOperations.executeWithoutResult(s -> entityStakeRepository.saveProgress(epochDay, SPLIT_ID, false));

        entityProperties.getPersist().setPendingRewardChunkSize(1);
        entityStakeCalculator.calculate();
        await().atMost(Durations.TEN_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(entityStakeRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparator()
                        .containsExactlyInAnyOrderElementsOf(expected));

        assertThat(entityStakeRepository.getLastProcessedEntityId(epochDay)).isEmpty();
    }

    @Test
    void calculateWithResumeFalseIgnoresPartialProgress() {
        final long epochDay = setupCrossChunkProxyScenario();

        transactionOperations.executeWithoutResult(s -> {
            entityStakeRepository.createEntityStateStart(stakingRewardAccountId, epochDay);
            entityStakeRepository.updateEntityStake(stakingRewardAccountId);
        });
        final List<EntityStake> expected = StreamSupport.stream(
                        entityStakeRepository.findAll().spliterator(), false)
                .toList();

        resetStakeTables();

        // Simulate leftover completed progress from a previous resume=true run.
        transactionOperations.executeWithoutResult(s -> entityStakeRepository.saveProgress(epochDay, TARGET_ID, true));

        entityProperties.getPersist().setPendingRewardChunkResume(false);
        entityProperties.getPersist().setPendingRewardChunkSize(1);
        entityStakeCalculator.calculate();
        await().atMost(Durations.TEN_SECONDS)
                .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
                .untilAsserted(() -> assertThat(entityStakeRepository.findAll())
                        .usingRecursiveFieldByFieldElementComparator()
                        .containsExactlyInAnyOrderElementsOf(expected));

        // resume=false never saves progress, and deleteCompletedProgress cleans up the old completed record.
        assertThat(entityStakeRepository.getLastProcessedEntityId(epochDay)).isEmpty();
    }

    /**
     * Low-id proxy staker ({@value #STAKER_ID}) stakes to high-id node target ({@value #TARGET_ID}) with chunk
     * boundary {@value #SPLIT_ID} between them so proxy totals must come from the full-period snapshot.
     */
    private long setupCrossChunkProxyScenario() {
        final long epochDay = 500L;
        final long previousEpochDay = epochDay - 1;
        final long nodeStakeTimestamp =
                DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        final long entityStakeLowerTimestamp =
                nodeStakeTimestamp - Duration.ofDays(2).toNanos();
        final long balanceTimestamp = nodeStakeTimestamp - 1000L;
        final long previousBalanceTimestamp = balanceTimestamp - 1000L;
        final long targetBalance = 200L * TINYBARS_IN_ONE_HBAR;
        final long stakerBalance = 100L * TINYBARS_IN_ONE_HBAR;

        domainBuilder.entity(stakingRewardAccountId, nodeStakeTimestamp - 10).persist();
        domainBuilder
                .entityStake()
                .customize(es -> es.id(stakingRewardAccountId)
                        .endStakePeriod(previousEpochDay)
                        .timestampRange(Range.atLeast(entityStakeLowerTimestamp)))
                .persist();

        domainBuilder
                .nodeStake()
                .customize(ns -> ns.epochDay(previousEpochDay)
                        .consensusTimestamp(nodeStakeTimestamp - 100)
                        .nodeId(1L))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeTimestamp)
                        .epochDay(epochDay)
                        .nodeId(1L)
                        .rewardRate(10L))
                .persist();

        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(5000L).id(new Id(balanceTimestamp, systemEntity.treasuryAccount())))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab ->
                        ab.balance(600L).id(new Id(previousBalanceTimestamp, EntityId.of(stakingRewardAccountId))))
                .persist();

        domainBuilder
                .entity(TARGET_ID, nodeStakeTimestamp - 1)
                .customize(e -> e.stakedNodeId(1L)
                        .stakePeriodStart(previousEpochDay)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp - 1)))
                .persist();
        domainBuilder
                .entityStake()
                .customize(es -> es.id(TARGET_ID)
                        .endStakePeriod(previousEpochDay)
                        .pendingReward(0L)
                        .stakedNodeIdStart(1L)
                        .stakeTotalStart(0L)
                        .timestampRange(Range.atLeast(entityStakeLowerTimestamp)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(targetBalance).id(new Id(balanceTimestamp, EntityId.of(TARGET_ID))))
                .persist();

        domainBuilder
                .entity(STAKER_ID, nodeStakeTimestamp - 2)
                .customize(e -> e.stakedAccountId(TARGET_ID)
                        .stakedNodeId(null)
                        .timestampRange(Range.atLeast(nodeStakeTimestamp - 2)))
                .persist();
        domainBuilder
                .accountBalance()
                .customize(ab -> ab.balance(stakerBalance).id(new Id(balanceTimestamp, EntityId.of(STAKER_ID))))
                .persist();

        return epochDay;
    }

    private void resetStakeTables() {
        jdbcOperations.update("delete from entity_stake_history");
        jdbcOperations.update("delete from entity_stake");
        jdbcOperations.update("delete from entity_stake_calculation_state");
    }
}
