-------------------
-- Add index to staking_reward_transfer to improve rest api query performance
-------------------
create index if not exists staking_reward_transfer__account_timestamp
    on staking_reward_transfer (account_id, consensus_timestamp);
