alter table if exists contract add column if not exists initcode bytea null;
alter table if exists contract_history add column if not exists initcode bytea null;
