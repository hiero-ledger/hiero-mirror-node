set local citus.multi_shard_modify_mode to 'sequential';
alter table transaction drop constraint if exists transaction__pk; --drops (consensus_timestamp, payer_account_id);
create index if not exists transaction__consensus_timestamp on
    transaction (consensus_timestamp);
create index if not exists transaction__payer_account_id_consensus_timestamp on
    transaction (payer_account_id, consensus_timestamp);
set local citus.multi_shard_modify_mode to 'parallel';
