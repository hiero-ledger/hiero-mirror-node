-- empty procedure
drop procedure if exists create_mirror_node_range_partitions();

create or replace procedure create_time_partition_for_table(table_name regclass) language plpgsql as
$$
declare
  one_sec_in_ns constant bigint := 10^9;
  next_from timestamp;
  next_from_ns bigint;
  next_to timestamp;
  next_to_ns bigint;
  partition_name text;
begin
    select to_timestamp into next_from_ns
    from mirror_node_time_partitions
    where parent = table_name::text
    order by to_timestamp desc
    limit 1;

    if not found then
        raise exception 'No partitions found for table %', table_name;
    end if;

    next_from := to_timestamp(next_from_ns / one_sec_in_ns);
    while next_from <= current_timestamp + ${partitionTimeInterval}::interval loop
        partition_name := format('%I_p%s', table_name, to_char(next_from, 'YYYY_MM'));
        next_to := next_from + ${partitionTimeInterval}::interval;
        next_to_ns := extract(epoch from next_to) * one_sec_in_ns;
        -- create partition
        execute format('create table %I partition of %I for values from (%L) to (%L)',
            partition_name,
            table_name,
            next_from_ns,
            next_to_ns
        );
        -- set storage parameters
        execute format('alter table %I set (autovacuum_vacuum_insert_scale_factor = 0,' ||
          ' autovacuum_vacuum_insert_threshold = 10000, parallel_workers = 4)', partition_name);
        raise notice 'Created partition %', partition_name;

        next_from := next_to;
        next_from_ns := next_to_ns;
    end loop;
end;
$$;

create or replace procedure create_mirror_node_time_partitions() language plpgsql as
$$
begin
  call create_time_partition_for_table('account_balance');
  call create_time_partition_for_table('token_balance');
end;
$$;
