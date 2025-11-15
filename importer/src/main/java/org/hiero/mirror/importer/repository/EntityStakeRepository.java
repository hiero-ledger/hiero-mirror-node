// SPDX-License-Identifier: Apache-2.0

package org.hiero.mirror.importer.repository;

import java.util.Optional;
import org.hiero.mirror.common.domain.entity.EntityStake;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface EntityStakeRepository extends CrudRepository<EntityStake, Long>, EntityStakeRepositoryCustom {

    @Query(value = "select endStakePeriod from EntityStake where id = ?1")
    Optional<Long> getEndStakePeriod(long stakingRewardAccount);

    @Modifying
    @Query(value = "lock table entity_stake in share row exclusive mode nowait", nativeQuery = true)
    @Transactional
    void lockFromConcurrentUpdates();

    @Query(
            value =
                    """
            with last_epoch_day as (
              select coalesce((select epoch_day from node_stake order by consensus_timestamp desc limit 1), -1) as epoch_day
            ), entity_stake_info as (
              select coalesce((select end_stake_period from entity_stake where id = ?1), -1) as end_stake_period
            ), staking_reward_account as (
              select (select id from entity where id = ?1) as account_id
            )
            select case when account_id is null then true
                        when (select epoch_day from last_epoch_day)
                          in (-1, (select end_stake_period from entity_stake_info)) then true
                        else false
                   end
            from staking_reward_account
            """,
            nativeQuery = true)
    boolean updated(long stakingRewardAccount);

    /**
     * Updates entity stake state based on the current entity stake state, the ending period node reward rate and the
     * entity state snapshot at the beginning of the new staking period.
     * <p>
     * Algorithm to update pending reward:
     * <p>
     * 1. IF there is no such row in entity_stake (new entity created in the ending stake period),
     * OR it didn't stake to a node for the ending staking period, the new pending reward is 0
     * <p>
     * 2. IF there is no node stake info for the node the entity staked to, the pending reward keeps the same
     * <p>
     * 3. IF the current stake_period_start > the day before last epochDay, no reward's should be earned, set pending
     * reward to 0
     * <p>
     * 4. IF the current stake_period_start equals to the day before last epochDay (either its staking metadata or
     * balance changed in the previous staking period), calculate the reward it has earned in the ending staking period
     * as its pending reward
     * <p>
     * 5. IF the current stake_period_start is more than 365 days before the last epochDay, deduct the reward earned
     * in staking period last epochDay - 365, and add the reward earned in last epochDay since staking reward is kept
     * for up to 365 days counting back from the last staking period.
     * <p>
     * 6. Otherwise, there's no staking metadata or balance change for the entity since the start of the ending staking
     * period, add the reward earned in the ending period to the current as the new pending reward
     */
    @Modifying
    @Query(
            value =
                    """
            drop index if exists entity_stake_temp__id;
            truncate table entity_stake_temp;

            with ending_period as (
              select epoch_day, consensus_timestamp
              from node_stake
              where epoch_day >= coalesce(
                (select end_stake_period + 1 from entity_stake where id = ?1),
                (
                  select epoch_day
                  from node_stake
                  where consensus_timestamp > (
                    select lower(timestamp_range) as timestamp from entity where id = ?1
                    union all
                    select lower(timestamp_range) as timestamp from entity_history where id = ?1
                    order by timestamp
                    limit 1
                  )
                  order by consensus_timestamp
                  limit 1
                )
              )
              order by consensus_timestamp
              limit 1
            ), ending_period_stake_state as (
              select
                id as entity_id,
                pending_reward,
                staked_node_id_start,
                (stake_total_start / 100000000) as stake_total_start_whole_bar,
                reward_rate
              from entity_stake es
              left join (
                select node_id, reward_rate
                from node_stake ns, ending_period
                where ns.consensus_timestamp = ending_period.consensus_timestamp
              ) node_stake on es.staked_node_id_start = node_id
            ), forfeited_period as (
              select distinct on(node_id) node_id, reward_rate
              from node_stake
              where epoch_day = (select epoch_day from ending_period) - 365
              order by node_id, consensus_timestamp
            ), proxy_staking as (
              select staked_account_id, sum(balance) as staked_to_me
              from entity_state_start
              where staked_account_id <> 0
              group by staked_account_id
            )
            insert into entity_stake_temp (end_stake_period, id, pending_reward,
              staked_node_id_start, staked_to_me, stake_total_start, timestamp_range)
            select
              epoch_day as end_stake_period,
              ess.id,
              (case
                 when coalesce(staked_node_id_start, -1) = -1 then 0
                 when reward_rate is null then pending_reward
                 when ess.stake_period_start > epoch_day - 1 then 0
                 when ess.stake_period_start = epoch_day - 1 then reward_rate * stake_total_start_whole_bar
                 when epoch_day - ess.stake_period_start > 365
                   then pending_reward + reward_rate * stake_total_start_whole_bar
                     - coalesce((select reward_rate from forfeited_period where node_id = staked_node_id_start), 0) * stake_total_start_whole_bar
                 else pending_reward + reward_rate * stake_total_start_whole_bar
                end) as pending_reward,
              ess.staked_node_id as staked_node_id_start,
              coalesce(ps.staked_to_me, 0) as staked_to_me,
              (case when ess.staked_node_id = -1 then 0
                    else ess.balance + coalesce(ps.staked_to_me, 0)
               end) as stake_total_start,
              int8range(ep.consensus_timestamp, null) as timestamp_range
            from entity_state_start ess
              left join ending_period_stake_state on entity_id = ess.id
              left join proxy_staking ps on ps.staked_account_id = ess.id,
              ending_period ep
            where ess.id = ?1 or ess.staked_node_id <> -1;

            create index if not exists entity_stake_temp__id on entity_stake_temp (id);

            -- history table
            insert into entity_stake_history (
                end_stake_period,
                id,
                pending_reward,
                staked_node_id_start,
                staked_to_me,
                stake_total_start,
                timestamp_range
              )
            select
              e.end_stake_period,
              e.id,
              e.pending_reward,
              e.staked_node_id_start,
              e.staked_to_me,
              e.stake_total_start,
              int8range(lower(e.timestamp_range), lower(t.timestamp_range))
            from entity_stake e
            join entity_stake_temp t using (id);

            -- current table
            insert into entity_stake (
                end_stake_period,
                id,
                pending_reward,
                staked_node_id_start,
                staked_to_me,
                stake_total_start,
                timestamp_range
              )
            select
              t.end_stake_period,
              t.id,
              t.pending_reward,
              t.staked_node_id_start,
              t.staked_to_me,
              t.stake_total_start,
              t.timestamp_range
            from entity_stake_temp t
            on conflict (id) do update
            set
              end_stake_period = excluded.end_stake_period,
              pending_reward = excluded.pending_reward,
              staked_node_id_start = excluded.staked_node_id_start,
              staked_to_me = excluded.staked_to_me,
              stake_total_start = excluded.stake_total_start,
              timestamp_range = excluded.timestamp_range;
            """,
            nativeQuery = true)
    @Transactional
    void updateEntityStake(long stakingRewardAccount);
}
