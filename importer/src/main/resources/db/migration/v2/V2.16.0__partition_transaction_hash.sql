alter table transaction_hash rename to transaction_hash_old;

create table if not exists transaction_hash
(
    consensus_timestamp bigint not null,
    hash                bytea  not null,
    payer_account_id    bigint not null,
    distribution_id     smallint not null
)
    partition by range (consensus_timestamp);

select create_distributed_table('transaction_hash', 'distribution_id', shard_count := ${hashShardCount});

select create_time_partitions(table_name :='public.transaction_hash',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});

INSERT INTO public.transaction_hash
SELECT *
FROM public.transaction_hash_old;

CREATE INDEX IF NOT EXISTS transaction_hash_part__hash
    ON public.transaction_hash using hash ((substring(hash from 1 for 32)))
    WITH (fillfactor = 75);

drop table public.transaction_hash_old;