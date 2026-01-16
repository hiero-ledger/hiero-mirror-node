// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import java.io.File;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.IterableAssert;
import org.hiero.mirror.common.domain.entity.Entity;
import org.hiero.mirror.common.domain.entity.EntityHistory;
import org.hiero.mirror.importer.DisableRepeatableSqlMigration;
import org.hiero.mirror.importer.EnabledIfV1;
import org.hiero.mirror.importer.util.Utility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;

@EnabledIfV1
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.68.1")
@DisableRepeatableSqlMigration
class FixStakePeriodStartMigrationTest extends AbstractStakingMigrationTest {

    private final JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.68.2.1__fix_stake_period_start.sql")
    private final File migrationSql;

    @Test
    void empty() {
        migrate();
        assertThat(findAllEntities()).isEmpty();
    }

    @Test
    void fixStakePeriodStart() {
        // given
        // two staking reward transfers for an account, the entity lower timestamp is the first reward payout's
        // timestamp
        // the migration should fix the stake period start
        long transferTimestamp1 = domainBuilder.timestamp() + 100L;
        long transferTimestamp2 = transferTimestamp1 + Duration.ofDays(1).toNanos();
        var entity = domainBuilder
                .entity()
                .customize(e -> e.stakedNodeId(1L)
                        .stakePeriodStart(Utility.getEpochDay(transferTimestamp2))
                        .timestampRange(Range.atLeast(transferTimestamp1)))
                .get();
        persistEntity(entity);

        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp1))
                .persist();
        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp2))
                .persist();

        // when
        migrate();

        // then
        entity.setStakePeriodStart(entity.getStakePeriodStart() - 1);
        assertEntities().containsExactly(entity);
    }

    @Test
    void fixStakePeriodStartHistory() {
        // given
        long createdTimestamp = domainBuilder.timestamp();
        long updateTimestamp1 = createdTimestamp + Duration.ofDays(5).toNanos();
        var entityHistoryWrapper = domainBuilder.entityHistory().customize(e -> e.createdTimestamp(createdTimestamp)
                .stakedNodeId(-1L)
                .stakePeriodStart(-1L)
                .timestampRange(Range.closedOpen(createdTimestamp, updateTimestamp1)));
        var entityHistory1 = entityHistoryWrapper.get();
        persistEntityHistory(entityHistory1);
        long rewardTimestamp1 = updateTimestamp1 + Duration.ofDays(2).toNanos();
        long rewardTimestamp2 = rewardTimestamp1 + Duration.ofDays(2).toNanos();
        long updateTimestamp2 = rewardTimestamp2 + Duration.ofDays(3).toNanos();
        var entityHistory2 = entityHistoryWrapper
                .customize(e -> e.stakedNodeId(0L)
                        .stakePeriodStart(Utility.getEpochDay(rewardTimestamp2))
                        .timestampRange(Range.closedOpen(updateTimestamp1, updateTimestamp2)))
                .get();
        persistEntityHistory(entityHistory2);
        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entityHistory1.getId()).consensusTimestamp(rewardTimestamp1))
                .persist();
        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entityHistory1.getId()).consensusTimestamp(rewardTimestamp2))
                .persist();
        var entity = domainBuilder
                .entity()
                .customize(e -> e.createdTimestamp(createdTimestamp)
                        .id(entityHistory1.getId())
                        .num(entityHistory1.getNum())
                        .timestampRange(Range.atLeast(updateTimestamp2)))
                .get();
        persistEntity(entity);

        // when
        migrate();

        // then
        entityHistory2.setStakePeriodStart(entityHistory2.getStakePeriodStart() - 1);
        assertEntities().containsExactly(entity);
        assertHistoryEntities().containsExactlyInAnyOrder(entityHistory1, entityHistory2);
    }

    @Test
    void noopWithCryptoUpdateAfterStakingRewardTransfer() {
        // given
        // account has crypto update transaction after its staking reward, the migration should not change its stake
        // period start
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder
                .entity()
                .customize(e ->
                        e.stakedNodeId(2L).stakePeriodStart(2000L).timestampRange(Range.atLeast(transferTimestamp)))
                .get();
        persistEntity(entity);

        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist();
        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopForDeletedAccount() {
        // given
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder
                .entity()
                .customize(e -> e.deleted(true)
                        .stakedNodeId(3L)
                        .stakePeriodStart(3000L)
                        .timestampRange(Range.atLeast(transferTimestamp + 1L)))
                .get();
        persistEntity(entity);
        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist(); // deleted

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopNoLongerStakedToNode() {
        // given
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder
                .entity()
                .customize(e ->
                        e.stakedNodeId(-1L).stakePeriodStart(-1L).timestampRange(Range.atLeast(transferTimestamp + 1L)))
                .get();
        persistEntity(entity);
        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist();

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopDeclinedReward() {
        // given
        long transferTimestamp = domainBuilder.timestamp() + 100L;
        var entity = domainBuilder
                .entity()
                .customize(e -> e.declineReward(true)
                        .stakedNodeId(5L)
                        .stakePeriodStart(5000L)
                        .timestampRange(Range.atLeast(transferTimestamp + 1L)))
                .get();
        persistEntity(entity);
        domainBuilder
                .stakingRewardTransfer()
                .customize(t -> t.accountId(entity.getId()).consensusTimestamp(transferTimestamp))
                .persist(); // decline reward

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    @Test
    void noopNoStakingRewardTransfer() {
        // given
        var entity = domainBuilder.entity().get();
        persistEntity(entity);

        // when
        migrate();

        // then
        assertEntities().containsExactly(entity);
    }

    private IterableAssert<Entity> assertEntities() {
        return assertThat(findAllEntities())
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "id", "declineReward", "stakedNodeId", "stakePeriodStart");
    }

    private IterableAssert<EntityHistory> assertHistoryEntities() {
        return assertThat((Iterable<EntityHistory>) findHistory(EntityHistory.class, "id", "entity"))
                .usingRecursiveFieldByFieldElementComparatorOnFields(
                        "id", "declineReward", "stakedNodeId", "stakePeriodStart", "timestampRange");
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }
}
