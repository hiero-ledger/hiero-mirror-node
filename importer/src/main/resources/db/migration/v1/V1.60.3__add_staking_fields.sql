alter table if exists contract
    add column if not exists decline_reward     boolean not null default false,
    add column if not exists staked_account_id  bigint default -1,
    add column if not exists staked_node_id     bigint default -1,
    add column if not exists stake_period_start bigint default -1;

alter table if exists contract_history
    add column if not exists decline_reward     boolean not null default false,
    add column if not exists staked_account_id  bigint default -1,
    add column if not exists staked_node_id     bigint default -1,
    add column if not exists stake_period_start bigint default -1;

alter table if exists entity
    add column if not exists decline_reward     boolean not null default false,
    add column if not exists staked_account_id  bigint default -1,
    add column if not exists staked_node_id     bigint default -1,
    add column if not exists stake_period_start bigint default -1;

alter table if exists entity_history
    add column if not exists decline_reward     boolean not null default false,
    add column if not exists staked_account_id  bigint default -1,
    add column if not exists staked_node_id     bigint default -1,
    add column if not exists stake_period_start bigint default -1;
