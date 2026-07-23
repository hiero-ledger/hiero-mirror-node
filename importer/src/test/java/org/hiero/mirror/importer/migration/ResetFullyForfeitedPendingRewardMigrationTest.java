// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.hiero.mirror.common.domain.entity.EntityStakeHistory;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.ImporterIntegrationTest;
import org.hiero.mirror.importer.TestUtils;
import org.hiero.mirror.importer.repository.EntityStakeRepository;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = ResetFullyForfeitedPendingRewardMigrationTest.Initializer.class)
@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@RequiredArgsConstructor
@Tag("migration")
final class ResetFullyForfeitedPendingRewardMigrationTest extends ImporterIntegrationTest {

    private final EntityStakeRepository entityStakeRepository;

    @Test
    void empty() {
        runMigration();
        assertThat(entityStakeRepository.findAll()).isEmpty();
        assertThat(findHistory(EntityStakeHistory.class)).isEmpty();
    }

    @Test
    void migrate() {
        // given the latest staking period is X, thus the staking reward retention window is [X-364, X]. Any reward
        // eared before X-364 should be forfeited. As a result, when a node's last full staking period is X-365 or
        // earlier, entities staked to it should have pending reward reset to 0
        final long endStakePeriod = Utility.getEpochDay(domainBuilder.timestamp());
        final long entityId1 = domainBuilder.id();
        final long entityId2 = domainBuilder.id();
        final long entityId3 = domainBuilder.id();
        final long nodeId1 = domainBuilder.id();
        final long nodeId2 = domainBuilder.id();
        final long nodeId3 = domainBuilder.id();
        final var entity800Stake = domainBuilder
                .entityStake()
                .customize(
                        e -> e.id(800L).pendingReward(0).stakedNodeIdStart(-1).endStakePeriod(endStakePeriod))
                .persist();
        final var entityStake1 = domainBuilder
                .entityStake()
                .customize(e -> e.id(entityId1)
                        .pendingReward(domainBuilder.number())
                        .stakedNodeIdStart(nodeId1)
                        .endStakePeriod(endStakePeriod))
                .persist();
        final var entityStake2 = domainBuilder
                .entityStake()
                .customize(e -> e.id(entityId2)
                        .pendingReward(-domainBuilder.number())
                        .stakedNodeIdStart(nodeId2)
                        .endStakePeriod(endStakePeriod))
                .persist();
        final var entityStake3 = domainBuilder
                .entityStake()
                .customize(e -> e.id(entityId3)
                        .pendingReward(domainBuilder.number())
                        .stakedNodeIdStart(nodeId3)
                        .endStakePeriod(endStakePeriod))
                .persist();
        final var entity800StakeHistory = domainBuilder
                .entityStakeHistory()
                .customize(
                        e -> e.id(800L).pendingReward(0).stakedNodeIdStart(-1).endStakePeriod(endStakePeriod - 1))
                .persist();
        final var entityStakeHistory1 = domainBuilder
                .entityStakeHistory()
                .customize(e -> e.id(entityId1)
                        .pendingReward(domainBuilder.number())
                        .stakedNodeIdStart(nodeId1)
                        .endStakePeriod(endStakePeriod - 1))
                .persist();
        final var entityStakeHistory2 = domainBuilder
                .entityStakeHistory()
                .customize(e -> e.id(entityId2)
                        .pendingReward(entityStake2.getPendingReward())
                        .stakedNodeIdStart(nodeId2)
                        .endStakePeriod(endStakePeriod - 1))
                .persist();
        final var entityStakeHistory3 = domainBuilder
                .entityStakeHistory()
                .customize(e -> e.id(entityId3)
                        .pendingReward(entityStake3.getPendingReward())
                        .stakedNodeIdStart(nodeId3)
                        .endStakePeriod(endStakePeriod - 1))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.nodeId(nodeId1).epochDay(endStakePeriod))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.nodeId(nodeId1).epochDay(endStakePeriod - 1))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.nodeId(nodeId1).epochDay(endStakePeriod - 1))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.nodeId(nodeId2).epochDay(endStakePeriod - 365))
                .persist();
        domainBuilder
                .nodeStake()
                .customize(ns -> ns.nodeId(nodeId3).epochDay(endStakePeriod - 364))
                .persist();

        // when
        runMigration();

        // then entityStake2's pending reward is reset to 0, entityStake3's pending reward is unchanged since the last
        // full staking period is the first period in the 365-day retention window
        entityStake2.setPendingReward(0L);
        assertThat(entityStakeRepository.findAll())
                .containsExactlyInAnyOrder(entity800Stake, entityStake1, entityStake2, entityStake3);
        assertThat(findHistory(EntityStakeHistory.class))
                .containsExactlyInAnyOrder(
                        entity800StakeHistory, entityStakeHistory1, entityStakeHistory2, entityStakeHistory3);
    }

    @SneakyThrows
    private void runMigration() {
        final var migrationFilepath = isV1()
                ? "v1/V1.126.0__reset_fully_forfeited_pending_reward.sql"
                : "v2/V2.31.0__reset_fully_forfetied_pending_reward.sql";
        final var file = TestUtils.getResource("db/migration/" + migrationFilepath);
        ownerJdbcTemplate.update(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
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
