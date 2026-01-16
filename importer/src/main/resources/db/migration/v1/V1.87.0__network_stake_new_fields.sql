-------------------
-- HIP-786: Enriched staking metadata exports
-------------------

-- Add new columns to network_stake
alter table if exists network_stake
    add column if not exists max_stake_rewarded bigint not null default 0,
    add column if not exists max_total_reward bigint not null default 0,
    add column if not exists reserved_staking_rewards bigint not null default 0,
    add column if not exists reward_balance_threshold bigint not null default 0,
    add column if not exists unreserved_staking_reward_balance bigint not null default 0;
