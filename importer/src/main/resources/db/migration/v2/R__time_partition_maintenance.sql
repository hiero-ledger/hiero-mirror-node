create or replace procedure create_mirror_node_time_partitions() as
$$
declare
    partition_info                    record;
    created_partition                 boolean;
    time_partition_pattern            varchar = '^(.*_timestamp|consensus_end)$';
begin
perform set_config('TimeZone', 'UTC', true);
for partition_info in
select distinct on (tp.parent_table) tp.parent_table,
    to_timestamp(tp.to_value::bigint / 1000000000.0)                                      as next_from,
    to_timestamp(tp.to_value::bigint / 1000000000.0) + interval ${partitiontimeinterval}  as next_to,
    tp.partition                                                                          as current_partition
from time_partitions tp
where tp.partition_column::varchar ~ time_partition_pattern
order by tp.parent_table, tp.from_value::bigint desc
    loop
    if current_timestamp + interval ${partitiontimeinterval} < partition_info.next_from then
    raise log 'skipping partition creation for time partition % from % to %',
    partition_info.parent_table, partition_info.next_from, partition_info.next_to;
continue;
end if;
select create_time_partitions(partition_info.parent_table, interval ${partitiontimeinterval},
                              partition_info.next_to, partition_info.next_from)
into created_partition;
if created_partition then
                perform apply_vacuum_settings_from_child(partition_info.parent_table, partition_info.current_partition);
end if;
commit;
raise log 'processed % for values from % to % created %',
                       partition_info.parent_table, partition_info.next_from,
                       partition_info.next_to, created_partition;
end loop;
end;
$$
language plpgsql;