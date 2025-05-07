create materialized view if not exists entity_state_start as
select
  balance,
  decline_reward,
  id,
  coalesce(staked_account_id, 0)   as staked_account_id,
  coalesce(staked_node_id, -1)     as staked_node_id,
  coalesce(stake_period_start, -1) as stake_period_start
from entity
where deleted is not true and type in ('ACCOUNT', 'CONTRACT');

create unique index if not exists entity_state_start__id on entity_state_start (id);
create index if not exists entity_state_start__staked_account_id
  on entity_state_start (staked_account_id) where staked_account_id <> 0;

create table if not exists entity_stake
(
  decline_reward_start boolean not null,
  end_stake_period     bigint  not null,
  id                   bigint  primary key,
  pending_reward       bigint  not null,
  staked_node_id_start bigint  not null,
  staked_to_me         bigint  not null,
  stake_total_start    bigint  not null
);
