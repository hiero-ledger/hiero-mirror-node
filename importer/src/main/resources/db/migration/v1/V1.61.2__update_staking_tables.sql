--- drop default of column staked_account_id from contract and contract_history
alter table if exists contract alter column staked_account_id drop default;
alter table if exists contract_history alter column staked_account_id drop default;

update contract set staked_account_id = 0 where staked_account_id = -1;
update contract_history set staked_account_id = 0 where staked_account_id = -1;

--- drop default of column staked_account_id from entity and entity_history
alter table if exists entity alter column staked_account_id drop default;
alter table if exists entity_history alter column staked_account_id drop default;

update entity set staked_account_id = 0 where staked_account_id = -1;
update entity_history set staked_account_id = 0 where staked_account_id = -1;

-- add columns to node_stake
alter table if exists node_stake
  add column max_stake bigint not null default -1,
  add column min_stake bigint not null default -1,
  add column stake_not_rewarded bigint not null default -1;

alter table if exists node_stake
  alter column max_stake drop default,
  alter column min_stake drop default,
  alter column stake_not_rewarded drop default;

-- change node_stake primary key to (consensus_timestamp, node_id)
alter table if exists node_stake
  drop constraint node_stake_pkey,
  add primary key (consensus_timestamp, node_id);
