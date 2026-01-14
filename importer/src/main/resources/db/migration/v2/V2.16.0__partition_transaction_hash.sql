alter table transaction_hash rename to transaction_hash_old;
drop index if exists transaction_hash__hash;

create table if not exists transaction_hash
(
    consensus_timestamp bigint not null,
    payer_account_id    bigint not null,
    distribution_id     smallint not null,
    hash                bytea  not null,
    hash_suffix         bytea
)
    partition by range (consensus_timestamp);

select create_distributed_table('transaction_hash', 'distribution_id', shard_count := ${hashShardCount});

select create_time_partitions(table_name :='public.transaction_hash',
                              partition_interval := ${partitionTimeInterval},
                              start_from := ${partitionStartDate}::timestamptz,
                              end_at := CURRENT_TIMESTAMP + ${partitionTimeInterval});

insert into transaction_hash (consensus_timestamp, payer_account_id, distribution_id, hash, hash_suffix)
select
    consensus_timestamp,
    payer_account_id,
    distribution_id,
    substring(hash from 1 for 32) as hash,
    case
        when octet_length(hash) > 32
            then substring(hash from 33)
        else null::bytea
        end as hash_suffix
from transaction_hash_old;

create index if not exists transaction_hash__hash
    on public.transaction_hash using hash (hash)
    with (fillfactor = 75);

select alter_distributed_table('transaction_hash', distribution_column := 'hash');
alter table transaction_hash drop column if exists distribution_id;

drop table if exists transaction_hash_old;
