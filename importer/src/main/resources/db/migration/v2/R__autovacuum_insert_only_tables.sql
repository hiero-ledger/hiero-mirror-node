-- -------------------
-- -- autovacuum insert-only tables more frequently to ensure most pages are visible for index-only scans
-- -------------------
--
create
or replace function apply_vacuum_settings_for_partitioned_table(parent regclass, vacuum_settings text)
    returns void as
$$
declare
partition_info record;
begin
    for partition_info in
    SELECT tp.partition
    from time_partitions tp
    where tp.parent_table = parent
    order by tp.from_value::bigint desc
    limit 2
    loop
        EXECUTE format(concat('alter table if exists %I set (', vacuum_settings, ')'),
                partition_info.partition);
    end loop;
end;
$$
language plpgsql;

-- Vacuum settings for partitioned tables

-- auto vacuum per each account balance snapshot if there are at least 10000 accounts' balance inserted
select apply_vacuum_settings_for_partitioned_table('account_balance'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=10000');
-- based on 12 contract actions per smart contract transaction and max 300 TPS
select apply_vacuum_settings_for_partitioned_table('contract_action'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=2160000');
-- base on average of 3 contract logs per smart contract transaction and max 300 TPS
select apply_vacuum_settings_for_partitioned_table('contract_log'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=540000');
-- max 300 smart contract transactions per second
select apply_vacuum_settings_for_partitioned_table('contract_result'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=180000');
-- based on average of 10 state changes per smart contract transaction and max 300 TPS
select apply_vacuum_settings_for_partitioned_table('contract_state_change'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=1800000');
-- autovacuum every 10 minutes at 10K TPS with average of 4 crypto transfers per transaction
select apply_vacuum_settings_for_partitioned_table('crypto_transfer'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=24000000');
-- adjust when ethereum transaction TPS becomes higher
select apply_vacuum_settings_for_partitioned_table('ethereum_transaction'::regclass, 'autovacuum_vacuum_insert_scale_factor=0.1');
-- autovacuum every 60 minutes with 2s interval record files
select apply_vacuum_settings_for_partitioned_table('record_file'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=1800');
-- threshold set based on recent stats so the table has about 2 to 3 automatic vacuums per day, adjust if there's more
-- daily staking reward claims
select apply_vacuum_settings_for_partitioned_table('staking_reward_transfer'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=4000');
-- autovacuum per each token balance snapshot if there are at least 10000 account token balance inserted
select apply_vacuum_settings_for_partitioned_table('token_balance'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=10000');
-- autovacuum every 10 minutes at 10K TPS crypto transfer transactions with two token transfer rows per transaction
select apply_vacuum_settings_for_partitioned_table('token_transfer'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=12000000');
-- autovacuum every 10 minutes at 10K TPS consensus submit message transactions
select apply_vacuum_settings_for_partitioned_table('topic_message'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=6000000');
-- autovacuum every 10 minutes at 10K TPS
select apply_vacuum_settings_for_partitioned_table('transaction'::regclass, 'autovacuum_vacuum_insert_scale_factor=0, autovacuum_vacuum_insert_threshold=6000000');

drop function apply_vacuum_settings_for_partitioned_table;
-- Vacuum settings for non-partitioned tables

-- auto vacuum daily with 15-minute interval account balance files
alter table if exists account_balance_file set (
    autovacuum_vacuum_insert_scale_factor = 0,
    autovacuum_vacuum_insert_threshold = 96
    );

-- autovacuum every 60 minutes with 2s interval record files. Note normally there is just one sidecar record
-- per each record file
alter table if exists sidecar_file set (
    autovacuum_vacuum_insert_scale_factor = 0,
    autovacuum_vacuum_insert_threshold = 1800
    );
