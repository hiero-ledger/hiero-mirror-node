alter table if exists contract_state_change add column if not exists migration boolean not null default false;
