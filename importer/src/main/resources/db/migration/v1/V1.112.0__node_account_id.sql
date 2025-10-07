alter table if exists node add column if not exists account_id bigint null;
alter table if exists node_history add column if not exists account_id bigint null;
