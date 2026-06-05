// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hiero.mirror.common.domain.SystemEntity;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Named
@RequiredArgsConstructor
class EntityStakeRepositoryCustomImpl implements EntityStakeRepositoryCustom {

    private static final String CLEANUP_TABLE_SQL = "delete from entity_state_start;";

    private static final String CLEANUP_PROXY_STAKING_SQL = "delete from entity_state_proxy_staking;";

    private static final String CREATE_PROXY_STAKING_SQL = """
            insert into entity_state_proxy_staking (staked_account_id, staked_to_me)
            select staked_account_id, sum(balance)
            from entity_state_start
            where staked_account_id <> 0
            group by staked_account_id;
            """;

    private static final String CREATE_ENTITY_STATE_START_SQL = """
            with entity_state as (
              select
                id,
                staked_account_id,
                staked_node_id,
                stake_period_start
              from entity
              where id = :stakingRewardAccount or (
                deleted is not true and
                type in ('ACCOUNT', 'CONTRACT') and
                timestamp_range @> :endPeriodTimestamp and
                (staked_account_id <> 0 or (decline_reward is false and staked_node_id <> -1))
              )
              union all
              select *
              from (
                select
                  distinct on (id)
                  id,
                  staked_account_id,
                  staked_node_id,
                  stake_period_start
                from entity_history
                where id <> :stakingRewardAccount and (
                  deleted is not true and
                  type in ('ACCOUNT', 'CONTRACT') and
                  timestamp_range @> :endPeriodTimestamp and
                  (staked_account_id <> 0 or (decline_reward is false and staked_node_id <> -1))
                )
                order by id, timestamp_range desc
              ) as latest_history
            ), balance_snapshot as (
              select distinct on (account_id) account_id, balance
              from account_balance
              where consensus_timestamp > :lowerBalanceTimestamp and consensus_timestamp <= :balanceSnapshotTimestamp
              order by account_id, consensus_timestamp desc
            )
            insert into entity_state_start (balance, id, staked_account_id, staked_node_id, stake_period_start)
            select
              coalesce(balance, 0) + coalesce(change, 0),
              id,
              coalesce(staked_account_id, 0),
              coalesce(staked_node_id, -1),
              coalesce(stake_period_start, -1)
            from entity_state
            left join balance_snapshot on account_id = id
            left join (
              select entity_id, sum(amount) as change
              from crypto_transfer
              where consensus_timestamp <= :endPeriodTimestamp and consensus_timestamp > :balanceSnapshotTimestamp
              group by entity_id
            ) as balance_change on entity_id = id;
            """;

    private static final String GET_EPOCH_TIMESTAMP_SQL = """
            select consensus_timestamp
            from node_stake
            where epoch_day = ?
            order by consensus_timestamp
            limit 1
            """;

    // Returns both epoch_day and consensus_timestamp for the next staking period to process.
    // Used only by getNextEndStakePeriod; createEntityStateStart receives the epoch_day from its caller.
    private static final String GET_NEXT_STAKE_PERIOD_SQL = """
            select epoch_day, consensus_timestamp
            from node_stake
            where epoch_day >= coalesce(
              (select end_stake_period + 1 from entity_stake where id = ?),
              (
                select epoch_day
                from node_stake
                where consensus_timestamp > (
                  select lower(timestamp_range) as timestamp from entity where id = $1
                  union all
                  select lower(timestamp_range) as timestamp from entity_history where id = $1
                  order by timestamp
                  limit 1
                )
                order by consensus_timestamp
                limit 1
              )
            )
            order by epoch_day
            limit 1
            """;

    // Filters completed=false intentionally: a completed=true record means the period finished in
    // entity_stake_calculation_state but deleteCompletedProgress has not run yet (e.g. crash between the final
    // saveProgress and deleteCompletedProgress). The next run falls back to id=0 and reprocesses the full period,
    // which is correct via the on-conflict upsert, at the cost of redundant work.
    private static final String GET_LAST_PROCESSED_ENTITY_ID_SQL = """
            select last_entity_id
            from entity_stake_calculation_state
            where end_stake_period = ?
              and completed is false
            """;

    private static final String DELETE_COMPLETED_PROGRESS_SQL =
            "delete from entity_stake_calculation_state where completed = true";

    private static final String UPSERT_PROGRESS_SQL = """
            insert into entity_stake_calculation_state (end_stake_period, last_entity_id, completed, updated_at)
            values (?, ?, ?, now())
            on conflict (end_stake_period) do update
            set last_entity_id = excluded.last_entity_id,
                completed = excluded.completed,
                updated_at = excluded.updated_at
            """;

    private static final String GET_CHUNK_UPPER_BOUND_ENTITY_ID_SQL = """
            with epoch_timestamp as (
              select consensus_timestamp
              from node_stake
              where epoch_day = :endStakePeriod
              order by consensus_timestamp
              limit 1
            ), candidates as (
              select id
              from (
                select id
                from entity
                where id <> :stakingRewardAccount
                  and id > :startEntityIdExclusive
                  and deleted is not true
                  and type in ('ACCOUNT', 'CONTRACT')
                  and timestamp_range @> (select consensus_timestamp from epoch_timestamp)
                  and (staked_account_id <> 0 or (decline_reward is false and staked_node_id <> -1))
                union
                (
                  select distinct on (id) id
                  from entity_history
                  where id <> :stakingRewardAccount
                    and id > :startEntityIdExclusive
                    and deleted is not true
                    and type in ('ACCOUNT', 'CONTRACT')
                    and timestamp_range @> (select consensus_timestamp from epoch_timestamp)
                    and (staked_account_id <> 0 or (decline_reward is false and staked_node_id <> -1))
                  order by id, timestamp_range desc
                )
              ) s
              order by id
              limit :chunkSize
            )
            select max(id) from candidates
            """;

    private static final long ONE_MONTH_IN_NS = Duration.ofDays(31).toNanos();

    private final AccountBalanceRepository accountBalanceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final SystemEntity systemEntity;

    @Modifying
    @Override
    @Transactional
    public void createEntityStateStart(long stakingRewardAccount, long endStakePeriod) {
        jdbcTemplate.execute(CLEANUP_TABLE_SQL);
        jdbcTemplate.execute(CLEANUP_PROXY_STAKING_SQL);

        Long endPeriodTimestamp;
        try {
            endPeriodTimestamp = jdbcTemplate.queryForObject(GET_EPOCH_TIMESTAMP_SQL, Long.class, endStakePeriod);
        } catch (EmptyResultDataAccessException ex) {
            return;
        }
        if (endPeriodTimestamp == null) {
            return;
        }
        // Add 1 for upper because the upper in getMaxConsensusTimestampInRange is exclusive
        long upperTimestamp = endPeriodTimestamp + 1;
        long lowerTimestamp = upperTimestamp - ONE_MONTH_IN_NS;
        long treasuryAccountId = systemEntity.treasuryAccount().getId();
        var balanceSnapshotTimestamp = accountBalanceRepository.getMaxConsensusTimestampInRange(
                lowerTimestamp, upperTimestamp, treasuryAccountId);
        if (balanceSnapshotTimestamp.isEmpty()) {
            return;
        }

        long lowerBalanceTimestamp = balanceSnapshotTimestamp.get() - ONE_MONTH_IN_NS;
        var params = new MapSqlParameterSource()
                .addValue("balanceSnapshotTimestamp", balanceSnapshotTimestamp.get())
                .addValue("endPeriodTimestamp", endPeriodTimestamp)
                .addValue("stakingRewardAccount", stakingRewardAccount)
                .addValue("lowerBalanceTimestamp", lowerBalanceTimestamp);
        namedParameterJdbcTemplate.update(CREATE_ENTITY_STATE_START_SQL, params);
        jdbcTemplate.execute(CREATE_PROXY_STAKING_SQL);
    }

    @Override
    public Optional<Long> getNextEndStakePeriod(long stakingRewardAccount) {
        return getNextStakePeriodInfo(stakingRewardAccount).map(StakePeriodInfo::epochDay);
    }

    @Override
    public Optional<Long> getLastProcessedEntityId(long endStakePeriod) {
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(GET_LAST_PROCESSED_ENTITY_ID_SQL, Long.class, endStakePeriod));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void saveProgress(long endStakePeriod, long lastEntityId, boolean completed) {
        jdbcTemplate.update(UPSERT_PROGRESS_SQL, endStakePeriod, lastEntityId, completed);
    }

    @Override
    public void deleteCompletedProgress() {
        jdbcTemplate.update(DELETE_COMPLETED_PROGRESS_SQL);
    }

    @Override
    public Optional<Long> getChunkUpperBoundEntityId(
            long stakingRewardAccount, long endStakePeriod, long startEntityIdExclusive, int chunkSize) {
        var params = new MapSqlParameterSource()
                .addValue("stakingRewardAccount", stakingRewardAccount)
                .addValue("endStakePeriod", endStakePeriod)
                .addValue("startEntityIdExclusive", startEntityIdExclusive)
                .addValue("chunkSize", chunkSize);
        List<Long> rows = namedParameterJdbcTemplate.query(
                GET_CHUNK_UPPER_BOUND_ENTITY_ID_SQL, params, new SingleColumnRowMapper<>(Long.class));
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Optional<StakePeriodInfo> getNextStakePeriodInfo(long stakingRewardAccount) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    GET_NEXT_STAKE_PERIOD_SQL,
                    (rs, rowNum) -> new StakePeriodInfo(rs.getLong("epoch_day"), rs.getLong("consensus_timestamp")),
                    stakingRewardAccount));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private record StakePeriodInfo(long epochDay, long consensusTimestamp) {}
}
