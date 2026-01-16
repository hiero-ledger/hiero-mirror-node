-- Add support for HTS contract precompiles including concept of internal transactions that are triggered by user submitted ones

alter table if exists transaction
    add column if not exists nonce                      integer default 0 not null,
    add column if not exists parent_consensus_timestamp bigint            null;
