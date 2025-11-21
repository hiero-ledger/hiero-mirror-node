set timezone to 'utc';

create table if not exists partition_config (
    parent_table       regclass    primary key,
    partition_interval interval    not null,
    start_from         timestamptz null
);

insert into partition_config(parent_table, partition_interval, start_from)
values
    ('public.account_balance', ${balancePartitionTimeInterval}, ${partitionStartDate}::timestamptz)
on conflict (parent_table) do update
    set partition_interval = excluded.partition_interval,
        start_from         = excluded.start_from;

insert into partition_config(parent_table, partition_interval, start_from)
values
    ('public.token_balance', ${balancePartitionTimeInterval}, ${partitionStartDate}::timestamptz)
on conflict (parent_table) do update
    set partition_interval = excluded.partition_interval,
        start_from         = excluded.start_from;

create or replace procedure create_mirror_node_time_partitions() as
$$
declare
partition_info         record;
    created_partition      boolean;
    time_partition_pattern varchar = '^(.*_timestamp|consensus_end)$';
begin
for partition_info in
select distinct on (tp.parent_table)
    tp.parent_table,
    to_timestamp(tp.to_value::bigint / 1e9) as next_from,
    to_timestamp(tp.to_value::bigint / 1e9)
    + coalesce(pc.partition_interval, interval ${partitionTimeInterval}) as next_to,
    tp.partition as current_partition,
    coalesce(pc.partition_interval, interval ${partitionTimeInterval}) as partition_interval
from time_partitions tp
    left join partition_config pc
on pc.parent_table = tp.parent_table
where tp.partition_column::varchar ~ time_partition_pattern
order by tp.parent_table, tp.from_value::bigint desc
    loop
    if current_timestamp + '30 days'::interval < partition_info.next_from then
        raise log 'Skipping partition creation for time partition % from % to %',
        partition_info.parent_table, partition_info.next_from, partition_info.next_to;
continue;
end if;

select create_time_partitions(
               partition_info.parent_table,
               partition_info.partition_interval,
               partition_info.next_to,
               partition_info.next_from
       )
into created_partition;

if created_partition then
            perform apply_vacuum_settings_from_child(
                partition_info.parent_table,
                partition_info.current_partition
            );
end if;

commit;

raise log 'Processed % for values from % to % created %',
                  partition_info.parent_table,
                  partition_info.next_from,
                  partition_info.next_to,
                  created_partition;
end loop;
end;
$$
language plpgsql;
