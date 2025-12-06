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

create or replace procedure partition_balance_table(table_name regclass)
language plpgsql
as
$$
declare
  one_sec_in_ns constant bigint := 10^9;

  partition_from    timestamp;
  partition_from_ns bigint;
  partition_name    text;
  partition_to      timestamp;
  partition_to_ns   bigint;
begin
  partition_from := date_trunc('month', ${partitionStartDate}::timestamp);

  while current_timestamp + ${balancePartitionTimeInterval}::interval >= partition_from loop
    partition_name := format('%I_p%s', table_name, to_char(partition_from, 'YYYY_MM'));
    partition_from_ns := extract(epoch from partition_from)::bigint * one_sec_in_ns;

    partition_to := partition_from + ${balancePartitionTimeInterval}::interval;
    partition_to_ns := extract(epoch from partition_to)::bigint * one_sec_in_ns;

    execute format(
      'create table %I partition of %I for values from (%L) to (%L)',
      partition_name,
      table_name,
      partition_from_ns,
      partition_to_ns
    );

    execute format(
      'alter table %I set (autovacuum_vacuum_insert_scale_factor = 0, ' ||
      'autovacuum_vacuum_insert_threshold = 10000, parallel_workers = 4)',
      partition_name
    );

    partition_from := partition_to;
  end loop;
end;
$$;

create or replace view mirror_node_time_partitions as
select
  child.relname as name,
  parent.relname as parent,
  substring(
    pg_get_expr(child.relpartbound, child.oid)
    from $$FROM \('(\d+)'\)$$
  )::bigint as from_timestamp,
  substring(
    pg_get_expr(child.relpartbound, child.oid)
    from $$TO \('(\d+)'\)$$
  )::bigint as to_timestamp
from pg_inherits
join pg_class as parent
  on pg_inherits.inhparent = parent.oid
join pg_class as child
  on pg_inherits.inhrelid = child.oid
where child.relkind = 'r'
  and pg_get_expr(child.relpartbound, child.oid)
        similar to $$FOR VALUES FROM \('[0-9]+'\) TO \('[0-9]+'\)$$
order by parent, from_timestamp;

create or replace function revert_partitioned_table(
  base_table_name text
) returns void
language plpgsql
as
$$
declare
  old_table_name text := base_table_name || '_old';
begin
  if to_regclass(old_table_name) is not null then
    execute format(
      'drop table if exists %I cascade',
      base_table_name
    );

    execute format(
      'alter table %I rename to %I',
      old_table_name,
      base_table_name
    );
  end if;
end;
$$;

-- Functions added by removed migration v1.89.0
drop function if exists create_full_account_balance_snapshot(bigint, bigint);
drop function if exists create_deduped_account_balance_snapshot(bigint, bigint);
drop function if exists create_full_token_balance_snapshot(bigint, bigint);
drop function if exists create_deduped_token_balance_snapshot(bigint, bigint);

-- Undo partitioning changes from removed migration v1.89.0
select revert_partitioned_table('account_balance');
select revert_partitioned_table('token_balance');
drop function if exists revert_partitioned_table(text);

alter table if exists account_balance rename to account_balance_old;
alter index if exists account_balance__pk rename to account_balance_old__pk;
select rename_old_partitions('account_balance_old');

alter table if exists token_balance rename to token_balance_old;
alter index if exists token_balance__pk rename to token_balance_old__pk;
alter index if exists token_balance__token_account_timestamp rename to token_balance_old__token_account_timestamp;
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

call partition_balance_table('account_balance');
call partition_balance_table('token_balance');
drop procedure if exists partition_balance_table(regclass);

alter table if exists account_balance
    add constraint account_balance__pk primary key (account_id, consensus_timestamp);
alter table if exists token_balance
    add constraint token_balance__pk primary key (account_id, token_id, consensus_timestamp);
create index if not exists token_balance__token_account_timestamp
    on token_balance (token_id, account_id, consensus_timestamp);


create index if not exists entity__balance_timestamp_idx
    on entity using brin (balance_timestamp)
    with (pages_per_range = 128, autosummarize = true);

create index if not exists token_account__balance_timestamp_idx
    on token_account using brin (balance_timestamp)
    with (pages_per_range = 128, autosummarize = true);
