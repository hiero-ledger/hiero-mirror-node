// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hiero.mirror.common.util.DomainUtils.TINYBARS_IN_ONE_HBAR;
import static org.hiero.mirror.importer.migration.RecalculatePendingRewardMigration.FIRST_NONZERO_REWARD_RATE_TIMESTAMP;

import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.util.DomainUtils;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.ImporterProperties;
import org.hiero.mirror.importer.ImporterProperties.HederaNetwork;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.68.3")
@DisableRepeatableSqlMigration
class RecalculatePendingRewardMigrationTest extends AbstractStakingMigrationTest {

    private final ImporterProperties importerProperties;
    private final RecalculatePendingRewardMigration migration;

    private long firstEpochDay;
    private long firstNonZeroRewardEpochDay;

    @AfterEach
    void teardown() {
        importerProperties.setNetwork(HederaNetwork.TESTNET);
    }

    @Test
    void empty() {
        migrate();
        assertThat(findAllEntityStakes()).isEmpty();
    }

    @Test
    void otherNetwork() {
        // given
        setupNodeStakeForNetwork(HederaNetwork.MAINNET);
        importerProperties.setNetwork(HederaNetwork.OTHER);
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(firstEpochDay))
                .get();
        persistEntities(entity);
        var entityStake = MigrationEntityStake.builder()
                .id(entity.getId())
                .pendingReward(100L)
                .stakeTotalStart(200 * TINYBARS_IN_ONE_HBAR)
                .build();
        persistEntityStakes(entityStake);

        // when
        migrate();

        // then
        assertThat(findAllEntityStakes()).containsExactly(entityStake);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void recalculate(String network) {
        // given
        setupNodeStakeForNetwork(network);
        // need 0.0.800's entity stake, also set the last calculated epoch day to the 4th non-zero reward period
        var entity = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .get();
        var entityStake800 = MigrationEntityStake.builder()
                .id(800)
                .endStakePeriod(firstNonZeroRewardEpochDay + 3)
                .build();
        var entity1 = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(firstEpochDay - 1))
                .get();
        persistEntities(entity, entity1);
        var entityStake1 = MigrationEntityStake.builder()
                .id(entity1.getId())
                .stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR)
                .build();
        persistEntityStakes(entityStake800, entityStake1);

        // when
        migrate();

        // then
        long totalRewardRate = 10L + 20L + 30L + 40L; // rewarded for 1st non-zero reward period to the 4th inclusively
        entityStake1.setPendingReward(entityStake1.getStakeTotalStart() / TINYBARS_IN_ONE_HBAR * totalRewardRate);
        assertThat(findAllEntityStakes())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void recalculateWhenStakeStartPeriodAfterFirst(String network) {
        // given
        setupNodeStakeForNetwork(network);
        // need 0.0.800's entity stake, also set the last calculated epoch day to the 4th non-zero reward period
        var entity = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .get();
        var entityStake800 = MigrationEntityStake.builder()
                .id(800)
                .endStakePeriod(firstNonZeroRewardEpochDay + 3)
                .build();
        var entity1 = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(firstNonZeroRewardEpochDay))
                .get();
        persistEntities(entity, entity1);

        var entityStake1 = MigrationEntityStake.builder()
                .id(entity1.getId())
                .stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR)
                .build();
        persistEntityStakes(entityStake800, entityStake1);

        // when
        migrate();

        // then
        long totalRewardRate = 20L + 30L + 40L; // rewarded for 2nd, 3rd, and 4th non-zero reward period
        entityStake1.setPendingReward(entityStake1.getStakeTotalStart() / TINYBARS_IN_ONE_HBAR * totalRewardRate);
        assertThat(findAllEntityStakes())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void recalculateWithStakingRewardPayout(String network) {
        // given
        setupNodeStakeForNetwork(network);
        // need 0.0.800's entity stake, also set the last calculated epoch day to the 4th non-zero reward period
        var entity = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .get();
        var entityStake800 = MigrationEntityStake.builder()
                .id(800)
                .endStakePeriod(firstNonZeroRewardEpochDay + 3)
                .build();
        long rewardPayoutEpochDay = firstNonZeroRewardEpochDay + 1;
        long stakePeriodStart = rewardPayoutEpochDay - 1;
        var entity1 = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(0L).stakePeriodStart(stakePeriodStart))
                .get();
        persistEntities(entity, entity1);
        var entityStake1 = MigrationEntityStake.builder()
                .id(entity1.getId())
                .stakeTotalStart(101 * TINYBARS_IN_ONE_HBAR)
                .build();
        persistEntityStakes(entityStake800, entityStake1);
        var payoutInstant = TestUtils.asStartOfEpochDay(rewardPayoutEpochDay).plusSeconds(300);
        var payoutTimestamp = DomainUtils.convertToNanosMax(payoutInstant);
        var rewardAmount = 2 * TINYBARS_IN_ONE_HBAR;
        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity1.getId())
                        .amount(rewardAmount)
                        .consensusTimestamp(payoutTimestamp)
                        .payerAccountId(entity1.toEntityId()))
                .persist();
        domainBuilder
                .cryptoTransfer()
                .customize(t -> t.entityId(entity1.getId()).amount(rewardAmount).consensusTimestamp(payoutTimestamp))
                .persist();
        domainBuilder
                .cryptoTransfer()
                .customize(t -> t.entityId(800L).amount(-rewardAmount).consensusTimestamp(payoutTimestamp))
                .persist();
        // a debit transfer from the account. As a result, the stake total start to use for stakePeriodStart + 1 should
        // be 101 - (2 - 1) = 100 hbar
        domainBuilder
                .cryptoTransfer()
                .customize(t -> t.entityId(entity1.getId())
                        .amount(-TINYBARS_IN_ONE_HBAR)
                        .consensusTimestamp(DomainUtils.convertToNanosMax(payoutInstant.plusSeconds(3600L))))
                .persist();

        // when
        migrate();

        // then
        // account1 earns reward for the 2nd non-zero reward period with a stake total of 100 hbar
        // account1 earns reward for the 3rd and the 4th non-zero reward periods with a stake total of 101 hbar
        long pendingReward = 100 * 20 + 101 * (30 + 40);
        entityStake1.setPendingReward(pendingReward);
        assertThat(findAllEntityStakes())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void recalculateForDeletedEntity(String network) {
        // given
        setupNodeStakeForNetwork(network);
        // need 0.0.800's entity stake, also set the last calculated epoch day to the 4th non-zero reward period
        var entity = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .get();
        var entityStake800 = MigrationEntityStake.builder()
                .id(800)
                .endStakePeriod(firstNonZeroRewardEpochDay + 3)
                .build();
        var entity1 = domainBuilder
                .entity()
                .customize(e -> e.deleted(true).stakedNodeId(0L).stakePeriodStart(firstNonZeroRewardEpochDay))
                .get();
        persistEntities(entity, entity1);
        var entityStake1 = MigrationEntityStake.builder()
                .id(entity1.getId())
                .stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR)
                .build();
        persistEntityStakes(entityStake800, entityStake1);

        // when
        migrate();

        // then
        assertThat(findAllEntityStakes())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void recalculateWhenDeclineReward(String network) {
        // given
        setupNodeStakeForNetwork(network);
        // need 0.0.800's entity stake, also set the last calculated epoch day to the 4th non-zero reward period
        var entity = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .get();
        var entityStake800 = MigrationEntityStake.builder()
                .id(800L)
                .endStakePeriod(firstNonZeroRewardEpochDay + 3)
                .build();
        var entity1 = domainBuilder
                .entity()
                .customize(e -> e.declineReward(true).stakedNodeId(0L).stakePeriodStart(firstNonZeroRewardEpochDay))
                .get();
        persistEntities(entity, entity1);
        var entityStake1 = MigrationEntityStake.builder()
                .id(entity1.getId())
                .stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR)
                .build();
        persistEntityStakes(entityStake800, entityStake1);

        // when
        migrate();

        // then
        assertThat(findAllEntityStakes())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void recalculateWhenNotStakedToNode(String network) {
        // given
        setupNodeStakeForNetwork(network);
        // need 0.0.800's entity stake, also set the last calculated epoch day to the 4th non-zero reward period
        var entity = domainBuilder
                .entity()
                .customize(e -> e.id(800L).num(800L).stakedNodeId(-1L).stakePeriodStart(-1L))
                .get();
        var entityStake800 = MigrationEntityStake.builder()
                .id(800L)
                .endStakePeriod(firstNonZeroRewardEpochDay + 3)
                .build();
        var entity1 = domainBuilder.entity().customize(e -> e.stakedNodeId(-1L)).get();
        persistEntities(entity, entity1);
        var entityStake1 = MigrationEntityStake.builder()
                .id(entity1.getId())
                .stakeTotalStart(100 * TINYBARS_IN_ONE_HBAR)
                .build();
        persistEntityStakes(entityStake800, entityStake1);

        // when
        migrate();

        // then
        assertThat(findAllEntityStakes())
                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "pendingReward")
                .containsExactlyInAnyOrder(entityStake800, entityStake1);
    }

    @ParameterizedTest
    @ValueSource(strings = {HederaNetwork.MAINNET, HederaNetwork.TESTNET})
    void recalculateWhenEntityStakeEmpty(String network) {
        // given pending reward is disabled, therefore the entity_stake table should be empty
        setupNodeStakeForNetwork(network);

        // when
        migrate();

        // then
        assertThat(findAllEntityStakes()).isEmpty();
    }

    private void migrate() {
        migration.doMigrate();
    }

    private long getNodeStakeTimestamp(long epochDay) {
        return DomainUtils.convertToNanosMax(
                TestUtils.asStartOfEpochDay(epochDay).plusNanos(100L));
    }

    private void persistEntities(Entity... entities) {
        for (var entity : entities) {
            persistEntity(entity);
        }
    }

    private void setupNodeStakeForNetwork(String network) {
        importerProperties.setNetwork(network);

        long firstNonZeroRewardTimestamp = FIRST_NONZERO_REWARD_RATE_TIMESTAMP.get(network);
        firstNonZeroRewardEpochDay = Utility.getEpochDay(firstNonZeroRewardTimestamp);
        firstEpochDay = firstNonZeroRewardEpochDay - 5;
        // set up node 0 reward rates starting from the first non-zero epoch day as 10, 20, 30, 40, 50
        for (int i = 0; i < 10; i++) {
            long rewardRateEpochDay = firstEpochDay + i;
            long rewardRate = Math.max(rewardRateEpochDay - firstNonZeroRewardEpochDay + 1, 0) * 10;
            // reward rate for epoch day is sent in node stake update tx at the beginning of epoch day + 1
            long consensusTimestamp = getNodeStakeTimestamp(rewardRateEpochDay + 1);
            domainBuilder
                    .nodeStake()
                    .customize(ns -> ns.consensusTimestamp(consensusTimestamp)
                            .epochDay(rewardRateEpochDay)
                            .nodeId(0L)
                            .rewardRate(rewardRate))
                    .persist();
        }
    }
}
