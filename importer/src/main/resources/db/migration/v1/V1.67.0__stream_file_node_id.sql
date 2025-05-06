alter table if exists account_balance_file add column if not exists node_id bigint;
update account_balance_file set node_id = node_account_id - 3;
alter table if exists account_balance_file
  alter column node_id set not null,
  drop column node_account_id;

alter table if exists event_file add column if not exists node_id bigint;
update event_file set node_id = node_account_id - 3;
alter table if exists event_file
  alter column node_id set not null,
  drop column node_account_id;

alter table if exists record_file add column if not exists node_id bigint;
update record_file set node_id = node_account_id - 3;
alter table if exists record_file
  alter column node_id set not null,
  drop column node_account_id;