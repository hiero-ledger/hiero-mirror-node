alter table if exists entity add column if not exists balance bigint null;
alter table if exists entity_history add column if not exists balance bigint null;

update entity
set balance = 0
where type in ('ACCOUNT', 'CONTRACT');
