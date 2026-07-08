with latest_staking_period as (
  select end_stake_period
  from entity_stake
  where id = 800
), impacted_node as (
  select node_id
  from node_stake
  group by node_id
  having max(epoch_day) < (select end_stake_period - 364 from latest_staking_period)
)
update entity_stake
set pending_reward = 0
where staked_node_id_start in (select * from impacted_node);