-------------------
-- Support topic message fragmentation by adding columns for ConsensusMessageChunkInfo
-------------------

-- add payer account and valid start time columns for transaction id. Add chunk count and total columns.
alter table if exists topic_message
    add column if not exists chunk_num int null,
    add column if not exists chunk_total int null,
    add column if not exists payer_account_id entity_id null,
    add column if not exists valid_start_timestamp nanos_timestamp null;

-- add transactions results
insert into t_transaction_results (proto_id, result) values (163,'INVALID_CHUNK_NUMBER');
insert into t_transaction_results (proto_id, result) values (164,'INVALID_CHUNK_TRANSACTION_ID');
