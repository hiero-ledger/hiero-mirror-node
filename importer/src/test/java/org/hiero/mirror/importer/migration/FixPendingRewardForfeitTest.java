// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;

import com.google.common.collect.Range;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.EntityType;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.TestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = FixPendingRewardForfeitTest.Initializer.class)
@RequiredArgsConstructor
@Tag("migration")
@DisableRepeatableSqlMigration
class FixPendingRewardForfeitTest extends AbstractStakingMigrationTest {

    private static final long STAKING_REWARD_ACCOUNT_ID = 800L;
    private static final long NODE_ID = 0L;

    private final JdbcOperations jdbcOperations;

    private long endStakePeriod;
    private long consensusTimestamp;
    private long forfeitedEpochDay;
    private long historyCutoffTimestamp;

    @Test
    void empty() {
        runMigration();
        assertThat(jdbcOperations.queryForObject("select count(*) from entity_stake", Long.class))
                .isZero();
    }

    @Test
    void fixPendingRewardWithChangedHistoricalStake() {
        setupStakingPeriod();
        // given - 45 HBAR through day 369, increased to 90 HBAR on day 370; prior pending reflects days 5-6
        var accountId = domainBuilder.id();
        var historicalStakeTotal = 45L * TINYBARS_IN_ONE_HBAR;
        var currentStakeTotal = 90L * TINYBARS_IN_ONE_HBAR;
        var forfeitRate = 300L;
        var periodSixRate = 300L;
        var currentRate = 200L;
        var priorPendingReward = priorPendingReward(historicalStakeTotal, forfeitRate, periodSixRate);
        var incorrectPendingReward =
                incorrectPendingRewardWithBug(priorPendingReward, currentStakeTotal, historicalStakeTotal, currentRate);

        persistImpactedAccount(accountId, 0L);
        persistAccountEntityStake(accountId, incorrectPendingReward, currentStakeTotal);
        persistForfeitedPeriodRates(forfeitRate, periodSixRate);
        persistNodeStake(endStakePeriod, currentRate);
        persistStakeHistory(accountId, forfeitedEpochDay, historicalStakeTotal, historyCutoffTimestamp);
        persistStakeHistory(accountId, endStakePeriod - 1, currentStakeTotal, consensusTimestamp - 1000);

        // when
        runMigration();

        // then
        var expectedPendingReward = correctPendingReward(
                priorPendingReward, currentStakeTotal, historicalStakeTotal, currentRate, forfeitRate);
        assertPendingReward(accountId, expectedPendingReward);
        assertThat(incorrectPendingReward).isNotEqualTo(expectedPendingReward);
    }

    @Test
    void fixPendingRewardWithConstantStake() {
        setupStakingPeriod();
        // given - stake total unchanged; prior pending reflects days 5-6 at 50 HBAR
        var accountId = domainBuilder.id();
        var stakeTotal = 50L * TINYBARS_IN_ONE_HBAR;
        var forfeitRate = 10L;
        var periodSixRate = 10L;
        var currentRate = 20L;
        var priorPendingReward = priorPendingReward(stakeTotal, forfeitRate, periodSixRate);
        var incorrectPendingReward = priorPendingReward + 9_999L;

        persistImpactedAccount(accountId, 1L);
        persistAccountEntityStake(accountId, incorrectPendingReward, stakeTotal);
        persistForfeitedPeriodRates(forfeitRate, periodSixRate);
        persistNodeStake(endStakePeriod, currentRate);
        persistStakeHistory(accountId, forfeitedEpochDay, stakeTotal, historyCutoffTimestamp);
        persistStakeHistory(accountId, endStakePeriod - 1, stakeTotal, consensusTimestamp - 1000);

        // when
        runMigration();

        // then
        var expectedPendingReward =
                correctPendingReward(priorPendingReward, stakeTotal, stakeTotal, currentRate, forfeitRate);
        assertPendingReward(accountId, expectedPendingReward);
    }

    @Test
    void fixPendingRewardForContractFromEntityHistory() {
        setupStakingPeriod();
        // given - contract qualifies via entity_history at the latest staking period consensus timestamp
        var contractId = domainBuilder.entityId();
        var stakeTotal = 30L * TINYBARS_IN_ONE_HBAR;
        var historicalStakeTotal = 45L * TINYBARS_IN_ONE_HBAR;
        var forfeitRate = 12L;
        var periodSixRate = 12L;
        var currentRate = 15L;
        var historyStakePeriodStart = 2L;
        var priorPendingReward = priorPendingReward(stakeTotal, forfeitRate, periodSixRate);
        var incorrectPendingReward =
                incorrectPendingRewardWithBug(priorPendingReward, stakeTotal, historicalStakeTotal, currentRate);

        domainBuilder
                .entity()
                .customize(e -> e.id(contractId.getId())
                        .num(contractId.getNum())
                        .stakedNodeId(NODE_ID)
                        .stakePeriodStart(endStakePeriod - 10)
                        .timestampRange(Range.atLeast(consensusTimestamp + 1000))
                        .type(EntityType.CONTRACT))
                .persist();
        domainBuilder
                .entityHistory()
                .customize(eh -> eh.id(contractId.getId())
                        .num(contractId.getNum())
                        .stakedNodeId(NODE_ID)
                        .stakePeriodStart(historyStakePeriodStart)
                        .timestampRange(Range.closedOpen(consensusTimestamp - 100, consensusTimestamp + 1))
                        .type(EntityType.CONTRACT))
                .persist();
        persistAccountEntityStake(contractId.getId(), incorrectPendingReward, stakeTotal);
        persistForfeitedPeriodRates(forfeitRate, periodSixRate);
        persistNodeStake(endStakePeriod, currentRate);
        persistStakeHistory(contractId.getId(), forfeitedEpochDay, stakeTotal, historyCutoffTimestamp);
        persistStakeHistory(contractId.getId(), endStakePeriod - 1, stakeTotal, consensusTimestamp - 1000);

        // when
        runMigration();

        // then
        var expectedPendingReward =
                correctPendingReward(priorPendingReward, stakeTotal, stakeTotal, currentRate, forfeitRate);
        assertPendingReward(contractId.getId(), expectedPendingReward);
    }

    @Test
    void noopWhenMissingHistoricalData() {
        setupStakingPeriod();
        // given - impacted account but no entity_stake_history rows to recalculate from
        var accountId = domainBuilder.id();
        var unchangedPendingReward = 12_345L;

        persistImpactedAccount(accountId, 0L);
        persistAccountEntityStake(accountId, unchangedPendingReward, 80L * TINYBARS_IN_ONE_HBAR);
        persistNodeStake(endStakePeriod, 100L);

        // when
        runMigration();

        // then
        assertPendingReward(accountId, unchangedPendingReward);
    }

    @Test
    void noopWhenStakePeriodStartIsNegative() {
        setupStakingPeriod();
        // given - account not staked to a node
        var accountId = domainBuilder.id();
        var unchangedPendingReward = 5_000L;

        domainBuilder
                .entity()
                .customize(e -> e.id(accountId)
                        .declineReward(false)
                        .stakedNodeId(-1L)
                        .stakePeriodStart(-1L)
                        .timestampRange(Range.atLeast(consensusTimestamp)))
                .persist();
        persistAccountEntityStake(accountId, unchangedPendingReward, 10L * TINYBARS_IN_ONE_HBAR);
        persistNodeStake(endStakePeriod, 50L);
        persistStakeHistory(accountId, endStakePeriod - 1, 10L * TINYBARS_IN_ONE_HBAR, consensusTimestamp - 1000);

        // when
        runMigration();

        // then
        assertPendingReward(accountId, unchangedPendingReward);
    }

    @Test
    void fixPendingRewardWhenStakePeriodStartIsZero() {
        setupStakingPeriod();
        // given - stake_period_start of 0 means staking began well before the forfeited period
        var accountId = domainBuilder.id();
        var stakeTotal = 20L * TINYBARS_IN_ONE_HBAR;
        var historicalStakeTotal = 45L * TINYBARS_IN_ONE_HBAR;
        var forfeitRate = 8L;
        var periodSixRate = 8L;
        var currentRate = 25L;
        var priorPendingReward = priorPendingReward(stakeTotal, forfeitRate, periodSixRate);
        var incorrectPendingReward =
                incorrectPendingRewardWithBug(priorPendingReward, stakeTotal, historicalStakeTotal, currentRate);

        persistImpactedAccount(accountId, 0L);
        persistAccountEntityStake(accountId, incorrectPendingReward, stakeTotal);
        persistForfeitedPeriodRates(forfeitRate, periodSixRate);
        persistNodeStake(endStakePeriod, currentRate);
        persistStakeHistory(accountId, forfeitedEpochDay, stakeTotal, historyCutoffTimestamp);
        persistStakeHistory(accountId, endStakePeriod - 1, stakeTotal, consensusTimestamp - 1000);

        // when
        runMigration();

        // then
        long expectedPendingReward =
                correctPendingReward(priorPendingReward, stakeTotal, stakeTotal, currentRate, forfeitRate);
        assertPendingReward(accountId, expectedPendingReward);
    }

    @Test
    void noopWhenStakingLessThan365Days() {
        setupStakingPeriod();
        // given - stake_period_start within 355 days of end_stake_period is not impacted
        var accountId = domainBuilder.id();
        var stakePeriodStart = endStakePeriod - 355;
        var unchangedPendingReward = 7_500L;

        persistImpactedAccount(accountId, stakePeriodStart);
        persistAccountEntityStake(accountId, unchangedPendingReward, 60L * TINYBARS_IN_ONE_HBAR);
        persistNodeStake(endStakePeriod, 80L);
        persistStakeHistory(accountId, endStakePeriod - 1, 60L * TINYBARS_IN_ONE_HBAR, consensusTimestamp - 1000);

        // when
        runMigration();

        // then
        assertPendingReward(accountId, unchangedPendingReward);
    }

    /**
     * pending_reward(dayN) = pending_reward(dayN - 1) + reward_rate(dayN) * stake_wb
     *                      - forfeit_rate(dayN - 365) * historical_wb(dayN - 365)
     */
    private long correctPendingReward(
            long priorPendingReward,
            long currentStakeTotal,
            long historicalStakeTotal,
            long currentRate,
            long forfeitRate) {
        return priorPendingReward
                + wholeBars(currentStakeTotal) * currentRate
                - wholeBars(historicalStakeTotal) * forfeitRate;
    }

    private long incorrectPendingRewardWithBug(
            long priorPendingReward, long currentStakeTotal, long historicalStakeTotal, long currentRate) {
        return priorPendingReward
                + wholeBars(currentStakeTotal) * currentRate
                - wholeBars(historicalStakeTotal) * currentRate;
    }

    /**
     * Realistic pending_reward(dayN - 1) with non-zero rates only on the forfeited day and the following day while
     * stake stayed at historical_wb.
     */
    private long priorPendingReward(long historicalStakeTotal, long forfeitRate, long periodSixRate) {
        long wholeBars = wholeBars(historicalStakeTotal);
        return wholeBars * forfeitRate + wholeBars * periodSixRate;
    }

    private long wholeBars(long stakeTotalTinybars) {
        return stakeTotalTinybars / TINYBARS_IN_ONE_HBAR;
    }

    private void setupStakingPeriod() {
        endStakePeriod = 370L;
        forfeitedEpochDay = endStakePeriod - 365;
        consensusTimestamp = toConsensusTimestamp(endStakePeriod + 1, 300);
        historyCutoffTimestamp = toConsensusTimestamp(forfeitedEpochDay + 1, 180);

        persistNodeStake(forfeitedEpochDay, 0L, historyCutoffTimestamp);
        persistStakingRewardAccount();
    }

    private void persistForfeitedPeriodRates(long forfeitRate, long periodSixRate) {
        persistNodeStake(forfeitedEpochDay, forfeitRate);
        persistNodeStake(forfeitedEpochDay + 1, periodSixRate);
    }

    private void assertPendingReward(long accountId, long expectedPendingReward) {
        assertThat(getPendingReward(accountId)).isEqualTo(expectedPendingReward);
    }

    private long getPendingReward(long accountId) {
        return jdbcOperations.queryForObject(
                "select pending_reward from entity_stake where id = ?", Long.class, accountId);
    }

    private void persistAccountEntityStake(long accountId, long pendingReward, long stakeTotalStart) {
        domainBuilder
                .entityStake()
                .customize(es -> es.id(accountId)
                        .endStakePeriod(endStakePeriod)
                        .pendingReward(pendingReward)
                        .stakedNodeIdStart(NODE_ID)
                        .stakeTotalStart(stakeTotalStart)
                        .timestampRange(Range.atLeast(consensusTimestamp - 2000)))
                .persist();
    }

    private void persistImpactedAccount(long accountId, long stakePeriodStart) {
        domainBuilder
                .entity()
                .customize(e -> e.id(accountId)
                        .declineReward(false)
                        .stakedNodeId(NODE_ID)
                        .stakePeriodStart(stakePeriodStart)
                        .timestampRange(Range.atLeast(consensusTimestamp)))
                .persist();
    }

    private void persistNodeStake(long epochDay, long rewardRate) {
        persistNodeStake(epochDay, rewardRate, toConsensusTimestamp(epochDay + 1, 200));
    }

    private void persistNodeStake(long epochDay, long rewardRate, long nodeStakeConsensusTimestamp) {
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.consensusTimestamp(nodeStakeConsensusTimestamp)
                        .epochDay(epochDay)
                        .nodeId(NODE_ID)
                        .rewardRate(rewardRate))
                .persist();
    }

    private void persistStakeHistory(
            long accountId, long historyEndStakePeriod, long stakeTotalStart, long lowerTimestamp) {
        domainBuilder
                .entityStakeHistory()
                .customize(esh -> esh.id(accountId)
                        .endStakePeriod(historyEndStakePeriod)
                        .stakedNodeIdStart(NODE_ID)
                        .stakeTotalStart(stakeTotalStart)
                        .timestampRange(Range.closedOpen(lowerTimestamp, lowerTimestamp + 1000)))
                .persist();
    }

    private void persistStakingRewardAccount() {
        domainBuilder
                .entity(STAKING_REWARD_ACCOUNT_ID, consensusTimestamp - 10_000)
                .customize(e -> e.num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .persist();
        domainBuilder
                .entityStake()
                .customize(es -> es.id(STAKING_REWARD_ACCOUNT_ID)
                        .endStakePeriod(endStakePeriod)
                        .timestampRange(Range.atLeast(consensusTimestamp)))
                .persist();
    }

    private long toConsensusTimestamp(long epochDay, long nanosOffset) {
        return DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(epochDay).plusNanos(nanosOffset));
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath =
                isV1() ? "v1/V1.125.1__fix_pending_reward_forfeit.sql" : "v2/V2.30.1__fix_pending_reward_forfeit.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.execute(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            var environment = configurableApplicationContext.getEnvironment();
            String version = environment.acceptsProfiles(Profiles.of("v2")) ? "2.30.1" : "1.125.1";
            TestPropertyValues.of("spring.flyway.target=" + version).applyTo(environment);
        }
    }
}
