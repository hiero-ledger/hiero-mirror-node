alter table if exists node
    add column if not exists decline_reward boolean not null default false;

alter table if exists node_history
    add column if not exists decline_reward boolean not null default false;
