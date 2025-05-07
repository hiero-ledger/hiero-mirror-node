create table if not exists network_freeze
(
    consensus_timestamp bigint primary key,
    end_time            bigint,
    file_hash           bytea    not null,
    file_id             bigint,
    payer_account_id    bigint   not null,
    start_time          bigint   not null,
    type                smallint not null
);
comment on table network_freeze is 'System transaction to freeze the network';
