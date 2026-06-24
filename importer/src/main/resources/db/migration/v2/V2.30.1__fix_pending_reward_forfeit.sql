-- Repair pending_reward for accounts/contracts that staked to a node for more than 365 days.
create temp table corrected_pending_reward as
with latest_staking_period as (
  select end_stake_period, lower(timestamp_range) as consensus_timestamp
  from entity_stake
  where id = 800
), impacted_entity as (
  select id
  from entity
  where type in ('ACCOUNT', 'CONTRACT')
    and decline_reward is not true
    and coalesce(staked_node_id, -1) <> -1
    and coalesce(stake_period_start, -1) < (select end_stake_period - 365 from latest_staking_period)
    and timestamp_range @> (select consensus_timestamp from latest_staking_period)
  union all
  select id
  from entity_history
  where type in ('ACCOUNT', 'CONTRACT')
    and decline_reward is not true
    and coalesce(staked_node_id, -1) <> -1
    and coalesce(stake_period_start, -1) < (select end_stake_period - 365 from latest_staking_period)
    and timestamp_range @> (select consensus_timestamp from latest_staking_period)
), node_reward_rate as (
  select distinct on (epoch_day, node_id) epoch_day, node_id, reward_rate
  from node_stake
  where epoch_day between (select end_stake_period - 364 from latest_staking_period)
                      and (select end_stake_period from latest_staking_period)
  order by epoch_day, node_id, consensus_timestamp
), stake_total_start_history as (
  select esh.id, esh.end_stake_period + 1 as stake_period, esh.staked_node_id_start, esh.stake_total_start
  from entity_stake_history esh
  join impacted_entity using (id)
  where lower(esh.timestamp_range) >= coalesce((
    select consensus_timestamp
    from node_stake
    where epoch_day = (select end_stake_period - 365 from latest_staking_period)
    order by consensus_timestamp
    limit 1), 0)
)

select h.id, sum((h.stake_total_start / 100000000) * r.reward_rate) as pending_reward
from stake_total_start_history h
join node_reward_rate r on r.node_id = h.staked_node_id_start and r.epoch_day = h.stake_period
group by h.id;

update entity_stake es
set pending_reward = c.pending_reward
from corrected_pending_reward c
where es.id = c.id
  and es.pending_reward <> c.pending_reward;
