-- Update hook_type enum since all lambda references have been removed
set local citus.multi_shard_modify_mode to 'sequential';

drop type if exists hook_type cascade;
create type hook_type as enum ('EVM');
alter table if exists hook add column type hook_type not null default 'EVM';
alter table if exists hook_history add column type hook_type not null default 'EVM';
