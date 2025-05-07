create or replace procedure partition_balance_table(table_name regclass) as
$$
declare
  one_sec_in_ns constant bigint := 10^9;

  partition_from timestamp;
  partition_from_ns bigint;
  partition_name text;
  partition_to timestamp;
  partition_to_ns bigint;
begin
  -- partitionTimeInterval should be at least 1 month, so align partitionStartDate to the beginning of the month
  partition_from := date_trunc('month', ${partitionStartDate}::timestamp);
  while current_timestamp + ${partitionTimeInterval}::interval >= partition_from loop
    partition_name := format('%I_p%s', table_name, to_char(partition_from, 'YYYY_MM'));
    partition_from_ns := extract(epoch from partition_from)::bigint * one_sec_in_ns;
    partition_to := partition_from + ${partitionTimeInterval}::interval;
    partition_to_ns := extract(epoch from partition_to)::bigint * one_sec_in_ns;
    execute format('create table %I partition of %I for values from (%L) to (%L)', partition_name, table_name,
      partition_from_ns, partition_to_ns);
    -- set storage parameters
    execute format('alter table %I set (autovacuum_vacuum_insert_scale_factor = 0,' ||
     ' autovacuum_vacuum_insert_threshold = 10000, parallel_workers = 4)', partition_name);
    partition_from := partition_to;
  end loop;
end;
$$ language plpgsql;

create or replace procedure partition_and_deduplicate_balance_table(table_name regclass) as
$$
declare
  account_balance_table_name constant regclass := 'account_balance';
  six_hour_in_ns constant bigint := 6 * 60 * 60 * 10^9;

  balance_timestamp bigint;
  is_account_balance boolean;
  partition_name text;
  prev_balance_timestamp bigint;
  prev_partition_name text;
  start_timestamp bigint;
begin
  call partition_balance_table(table_name);

  -- keep and deduplicate the last 6 hours balance info
  select consensus_timestamp into start_timestamp
  from account_balance_file
  where consensus_timestamp >= (select max(consensus_timestamp) from account_balance_file) - six_hour_in_ns
  order by consensus_timestamp
  limit 1;
  if not found then
    raise notice 'No balance info found';
    return;
  end if;

  is_account_balance := table_name = account_balance_table_name;

  -- save the start_timestamp to the balance column with sentinel value (-1, -1) for (account_id, consensus_timestamp)
  if is_account_balance then
    insert into account_balance_old (account_id, balance, consensus_timestamp)
    values (-1, start_timestamp, -1);
  end if;

  for balance_timestamp in
    select consensus_timestamp
    from account_balance_file
    where consensus_timestamp >= start_timestamp
    order by consensus_timestamp
  loop
    -- find the partition
    select name into partition_name
    from mirror_node_time_partitions
    where parent = table_name::text and balance_timestamp >= from_timestamp
    order by from_timestamp desc
    limit 1;

    if not found then
      raise exception 'No partition found for timestamp %', balance_timestamp;
    end if;

    if prev_partition_name is null or prev_partition_name <> partition_name then
      -- first snapshot in the partition, copy all rows in the snapshot and any accounts deleted in between
      -- prev_balance_timestamp and balance_timestamp
      if is_account_balance then
        perform create_full_account_balance_snapshot(balance_timestamp, prev_balance_timestamp);
      else
        perform create_full_token_balance_snapshot(balance_timestamp, prev_balance_timestamp);
      end if;
    else
      -- deduplicate
      if is_account_balance then
        perform create_deduped_account_balance_snapshot(balance_timestamp, prev_balance_timestamp);
      else
        perform create_deduped_token_balance_snapshot(balance_timestamp, prev_balance_timestamp);
      end if;
    end if;

    prev_balance_timestamp := balance_timestamp;
    prev_partition_name := partition_name;
  end loop;
end;
$$ language plpgsql;

set timezone to 'UTC';

-- account_balance
alter table account_balance rename to account_balance_old;
create table if not exists account_balance (like account_balance_old) partition by range (consensus_timestamp);
call partition_and_deduplicate_balance_table('account_balance');
alter table account_balance add primary key (account_id, consensus_timestamp);

-- token_balance
alter table token_balance rename to token_balance_old;
create table if not exists token_balance (like token_balance_old) partition by range (consensus_timestamp);
call partition_and_deduplicate_balance_table('token_balance');
alter table token_balance add primary key (account_id, token_id, consensus_timestamp);

-- drop the procedures in reverse order
drop procedure if exists partition_and_deduplicate_balance_table(regclass);
drop procedure if exists partition_balance_table(regclass);
