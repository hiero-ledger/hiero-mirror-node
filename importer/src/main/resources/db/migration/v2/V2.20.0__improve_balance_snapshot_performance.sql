set local citus.multi_shard_modify_mode to 'sequential';

create or replace function rename_old_partitions(parent_table_name text)
returns void
language plpgsql
as
$$
declare
  r record;
  new_name text;
begin
  for r in
    select
      n.nspname as schema_name,
      c.relname as child_name
    from pg_inherits i
    join pg_class c
      on c.oid = i.inhrelid
    join pg_class p
      on p.oid = i.inhparent
    join pg_namespace n
      on n.oid = c.relnamespace
    where p.relname = parent_table_name
  loop
    if r.child_name like '%_old_p%' then
      continue;
    end if;

    new_name := regexp_replace(r.child_name, '^(.*?)(_p.*)$', '\1_old\2');

    execute format(
      'alter table %I.%I rename to %I',
      r.schema_name,
      r.child_name,
      new_name
    );
  end loop;
end;
$$;

alter table account_balance rename to account_balance_old;
alter table token_balance  rename to token_balance_old;
select rename_old_partitions('account_balance_old');
select rename_old_partitions('token_balance_old');
drop function rename_old_partitions(text);


create table if not exists account_balance
(
  account_id          bigint not null,
  balance             bigint not null,
  consensus_timestamp bigint not null
) partition by range (consensus_timestamp);

comment on table account_balance is
  'Account balances (historical) in tinybars at different consensus timestamps';

create table if not exists token_balance
(
  account_id          bigint not null,
  balance             bigint not null,
  consensus_timestamp bigint not null,
  token_id            bigint not null
) partition by range (consensus_timestamp);

comment on table token_balance is
  'Crypto account token balances';

select create_distributed_table('account_balance', 'account_id', colocate_with => 'entity');
select create_distributed_table('token_balance',  'account_id', colocate_with => 'entity');

select create_time_partitions(
  table_name         := 'public.account_balance',
  partition_interval := ${balancePartitionTimeInterval},
  start_from         := ${partitionStartDate}::timestamptz,
  end_at             := current_timestamp + ${balancePartitionTimeInterval}
);

select create_time_partitions(
  table_name         := 'public.token_balance',
  partition_interval := ${balancePartitionTimeInterval},
  start_from         := ${partitionStartDate}::timestamptz,
  end_at             := current_timestamp + ${balancePartitionTimeInterval}
);

create index if not exists entity__balance_timestamp_idx
  on entity using brin (balance_timestamp)
  with (pages_per_range = 128, autosummarize = true);

create index if not exists token_account__balance_timestamp_idx
  on token_account using brin (balance_timestamp)
  with (pages_per_range = 128, autosummarize = true);
