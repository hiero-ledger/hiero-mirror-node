// SPDX-License-Identifier: Apache-2.0

package com.hedera.mirror.importer.repository;

import jakarta.inject.Named;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Named
@RequiredArgsConstructor
class EntityStakeRepositoryCustomImpl implements EntityStakeRepositoryCustom {

    private static final String CLEANUP_TABLE_SQL =
            """
            drop index if exists entity_state_start__id;
            drop index if exists entity_state_start__staked_account_id;
            truncate entity_state_start;
            """;
    private static final String CREATE_ENTITY_STATE_START_SQL =
            """
            with entity_state as (
              select
                id,
                staked_account_id,
                staked_node_id,
                stake_period_start
              from entity
              where id = 800 or (
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
                where id <> 800 and (
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
    private static final String CREATE_TABLE_INDEX_DDL =
            """
            create index if not exists entity_state_start__id on entity_state_start (id);
            create index if not exists entity_state_start__staked_account_id
              on entity_state_start (staked_account_id) where staked_account_id <> 0;
            """;
    private static final String GET_END_PERIOD_TIMESTAMP_SQL =
            """
            select consensus_timestamp
            from node_stake
            where epoch_day >= coalesce(
              (select end_stake_period + 1 from entity_stake where id = 800),
              (
                select epoch_day
                from node_stake
                where consensus_timestamp > (
                  select lower(timestamp_range) as timestamp from entity where id = 800
                  union all
                  select lower(timestamp_range) as timestamp from entity_history where id = 800
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
    private static final long ONE_MONTH_IN_NS = Duration.ofDays(31).toNanos();

    private final AccountBalanceRepository accountBalanceRepository;
    private final JdbcTemplate jdbcTemplate;

    @Modifying
    @Override
    @Transactional
    public void createEntityStateStart() {
        jdbcTemplate.execute(CLEANUP_TABLE_SQL);

        var endPeriodTimestamp = getEndPeriodTimestamp();
        if (endPeriodTimestamp.isEmpty()) {
            return;
        }

        // Add 1 for upper because the upper in getMaxConsensusTimestampInRange is exclusive
        long upperTimestamp = endPeriodTimestamp.get() + 1;
        long lowerTimestamp = upperTimestamp - ONE_MONTH_IN_NS;
        var balanceSnapshotTimestamp =
                accountBalanceRepository.getMaxConsensusTimestampInRange(lowerTimestamp, upperTimestamp, 2L);
        if (balanceSnapshotTimestamp.isEmpty()) {
            return;
        }

        long lowerBalanceTimestamp = balanceSnapshotTimestamp.get() - ONE_MONTH_IN_NS;
        var params = new MapSqlParameterSource()
                .addValue("balanceSnapshotTimestamp", balanceSnapshotTimestamp.get())
                .addValue("endPeriodTimestamp", endPeriodTimestamp.get())
                .addValue("lowerBalanceTimestamp", lowerBalanceTimestamp);
        var namedParameterJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        namedParameterJdbcTemplate.update(CREATE_ENTITY_STATE_START_SQL, params);
        jdbcTemplate.execute(CREATE_TABLE_INDEX_DDL);
    }

    private Optional<Long> getEndPeriodTimestamp() {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(GET_END_PERIOD_TIMESTAMP_SQL, Long.class));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
