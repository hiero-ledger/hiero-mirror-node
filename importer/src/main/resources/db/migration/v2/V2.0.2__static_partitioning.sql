-- configure session to ensure partitions are created with bounds in UTC
SET timezone TO 'UTC';

CREATE FUNCTION nanos_to_timestamptz(nanos bigint) RETURNS timestamptz
    LANGUAGE plpgsql AS
    $$
DECLARE
value timestamptz;
BEGIN
select to_timestamp(nanos / 1000000000.0)
into value;
return value;
END;
    $$;
CREATE CAST (bigint AS timestamptz) WITH FUNCTION nanos_to_timestamptz(bigint);

CREATE FUNCTION timestamptz_to_nanos(ts timestamptz) RETURNS bigint
    LANGUAGE plpgsql AS
    $$
DECLARE
value bigint;
BEGIN
select extract(epoch from ts) * 1000000000
into value;
return value;
END;
    $$;
CREATE CAST (timestamptz AS bigint) WITH FUNCTION timestamptz_to_nanos(timestamptz);

create or replace function apply_vacuum_settings_from_child(parent regclass, child regclass) returns void as
$$
declare
    current_partition_vacuum_settings text;
    new_partition                     regclass;
    begin
        select tp.partition
        from time_partitions tp
        where tp.parent_table = parent order by tp.from_value::bigint desc limit 1
        into new_partition;
        select string_agg(options.option, ',') as vacuum_settings
        from (select unnest(reloptions) as option
              from pg_class where relname=child::text) options
        where options.option ILIKE '%vacuum%'
        into current_partition_vacuum_settings;

        if new_partition != child and length(coalesce(current_partition_vacuum_settings, '')) > 0 then
            execute format(concat('alter table if exists %I set (', current_partition_vacuum_settings, ')'), new_partition);
        end if;
    end;
$$
language plpgsql;

create or replace procedure create_mirror_node_time_partitions() as
$$
declare
    partition_info                    record;
    created_partition                 boolean;
    time_partition_pattern            varchar = '^(.*_timestamp|consensus_end)$';
begin
    for partition_info in
        SELECT distinct on (tp.parent_table) tp.parent_table,
               to_timestamp(tp.to_value::bigint / 1000000000.0)                                      as next_from,
               to_timestamp(tp.to_value::bigint / 1000000000.0) + interval ${partitionTimeInterval}  as next_to,
               tp.partition                                                                          as current_partition
        from time_partitions tp
        where tp.partition_column::varchar ~ time_partition_pattern
        order by tp.parent_table, tp.from_value::bigint desc
        loop
            if CURRENT_TIMESTAMP + interval ${partitionTimeInterval} < partition_info.next_from then
                raise log 'Skipping partition creation for time partition % from % to %',
                          partition_info.parent_table, partition_info.next_from, partition_info.next_to;
                continue;
            end if;
            select create_time_partitions(partition_info.parent_table, interval ${partitionTimeInterval},
                                          partition_info.next_to, partition_info.next_from)
            into created_partition;
            if created_partition then
                perform apply_vacuum_settings_from_child(partition_info.parent_table, partition_info.current_partition);
            end if;
            commit;
            raise log 'Processed % for values from % to % created %',
                       partition_info.parent_table, partition_info.next_from,
                       partition_info.next_to, created_partition;
        end loop;
end;
$$
language plpgsql;

-------------------
-- Add non-repeatable partitioning logic to large tables.
-------------------
select create_time_partitions(table_name :='public.account_balance',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.assessed_custom_fee',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_action',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_log',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_result',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_state_change',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.contract_transaction',
                              partition_interval := interval ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.crypto_transfer',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.entity_transaction',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.ethereum_transaction',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.file_data', partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.network_freeze',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.record_file',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.staking_reward_transfer',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.token_balance',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.token_transfer',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.topic_message',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
select create_time_partitions(table_name :='public.transaction',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});
