delete from entity_stake where id <> 800 and (staked_node_id_start = -1 or decline_reward_start is true);

delete from entity_stake_history where id <> 800 and (staked_node_id_start = -1 or decline_reward_start is true);

alter table if exists entity_stake
    drop column if exists decline_reward_start;
alter table if exists entity_stake_history
    drop column if exists decline_reward_start;