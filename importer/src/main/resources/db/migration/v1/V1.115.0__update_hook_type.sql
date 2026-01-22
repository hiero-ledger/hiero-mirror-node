-- Update hook_type enum since all lambda references have been removed
drop type if exists hook_type cascade;
create type hook_type as enum ('EVM');
alter table if exists hook add column type hook_type not null default 'EVM';
alter table if exists hook_history add column type hook_type not null default 'EVM';
