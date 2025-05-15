alter table if exists entity
  add column if not exists balance_timestamp bigint null;

alter table if exists entity_history
  add column if not exists balance_timestamp bigint null;

alter table if exists token_account
  add column if not exists balance_timestamp bigint null;

alter table if exists token_account_history
  add column if not exists balance_timestamp bigint null;

update entity
  set balance_timestamp = (select consensus_end from record_file order by consensus_end desc limit 1)
  where balance is not null;

update entity_history
  set balance_timestamp = upper(timestamp_range) - 1
  where balance is not null;

update token_account
  set balance_timestamp = coalesce((select consensus_end from record_file order by consensus_end desc limit 1), 0);

update token_account_history
  set balance_timestamp = upper(timestamp_range) - 1;

alter table if exists token_account
  alter column balance_timestamp set not null;
alter table if exists token_account_history
  alter column balance_timestamp set not null;