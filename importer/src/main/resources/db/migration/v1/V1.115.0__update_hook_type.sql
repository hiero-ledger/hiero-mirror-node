-- Update hook_type enum since all lambda references have been removed
alter table if exists hook drop column if exists type;
alter table if exists hook_history drop column if exists type;
drop table if exists hook_temp;
drop type if exists hook_type;

create type hook_type as enum ('EVM');
alter table if exists hook add column type hook_type not null default 'EVM';
alter table if exists hook_history add column type hook_type not null default 'EVM';
