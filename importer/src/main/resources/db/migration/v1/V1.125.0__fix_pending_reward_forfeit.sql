-- Repair pending_reward over-deducted by the forfeit bug for accounts staking continuously for over 365 days.
--
-- When an account had been staking to the same node for over 365 days, updateEntityStake forfeits the reward it earned
-- in the staking period 365 days earlier. The buggy logic computed the forfeited amount using the account's stake total
-- whole bar at the start of the ending period (the prior entity_stake row's stake_total_start) instead of the stake
-- total it actually had during the forfeited period:
--   buggy:   pending(N) = pending(N-1) + reward_rate(N) * stake_wb(N-1) - forfeit_rate(N-365) * stake_wb(N-1)
--   correct: pending(N) = pending(N-1) + reward_rate(N) * stake_wb(N-1) - forfeit_rate(N-365) * stake_wb_hist(N-365)
-- The per-period over-deduction forfeit_rate(N-365) * (stake_wb(N-1) - stake_wb_hist(N-365)) accumulates period over
-- period, so the running sum of all over-deductions is added back into the current entity_stake.pending_reward.
-- entity_stake_history is read only (for the prior-period multiplier and the historical stake at the forfeited period);
-- it is not modified by this migration.
with stake_row as (
  select id, end_stake_period, staked_node_id_start, stake_total_start, lower(timestamp_range) as period_ts,
         false as is_current
  from entity_stake_history
  union all
  select id, end_stake_period, staked_node_id_start, stake_total_start, lower(timestamp_range) as period_ts,
         true as is_current
  from entity_stake
), ordered as (
  select s.*,
    lag(stake_total_start) over (partition by id order by end_stake_period) as prev_stake_total_start
  from stake_row s
), with_metadata as (
  select o.*,
    coalesce(
      (select e.stake_period_start from entity e
        where e.id = o.id and e.timestamp_range @> o.period_ts),
      (select eh.stake_period_start from entity_history eh
        where eh.id = o.id and eh.timestamp_range @> o.period_ts
        order by lower(eh.timestamp_range) desc limit 1)
    ) as stake_period_start,
    (select reward_rate from node_stake
      where epoch_day = o.end_stake_period and node_id = o.staked_node_id_start
      order by consensus_timestamp limit 1) as ending_reward_rate,
    fp.reward_rate as forfeit_reward_rate,
    fp.consensus_timestamp as forfeit_ts
  from ordered o
  left join lateral (
    select reward_rate, consensus_timestamp
    from node_stake
    where epoch_day = o.end_stake_period - 365 and node_id = o.staked_node_id_start
    order by consensus_timestamp
    limit 1
  ) fp on true
), over_deduction as (
  select m.*,
    case
      when coalesce(m.staked_node_id_start, -1) <> -1
        and m.ending_reward_rate is not null
        and m.forfeit_reward_rate is not null
        and m.prev_stake_total_start is not null
        and m.stake_period_start is not null
        and (m.end_stake_period - m.stake_period_start) > 365
      then m.forfeit_reward_rate * (
            (m.prev_stake_total_start / 100000000)
            - coalesce((
                select esh.stake_total_start / 100000000
                from entity_stake_history esh
                where esh.id = m.id and lower(esh.timestamp_range) < m.forfeit_ts
                order by lower(esh.timestamp_range) desc
                limit 1), 0))
      else 0
    end as over_deduction
  from with_metadata m
), corrected as (
  select id, period_ts, is_current,
    sum(over_deduction) over (
      partition by id order by end_stake_period
      rows between unbounded preceding and current row) as correction
  from over_deduction
)
update entity_stake es
set pending_reward = es.pending_reward + c.correction
from corrected c
where c.is_current and c.correction <> 0 and c.id = es.id and lower(es.timestamp_range) = c.period_ts;
