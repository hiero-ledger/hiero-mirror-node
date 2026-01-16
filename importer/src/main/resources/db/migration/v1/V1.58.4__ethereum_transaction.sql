-------------------
-- Add ethereum_transaction table to support multiple ethereum transaction types
-------------------

create table if not exists ethereum_transaction
(
    access_list              bytea    null,
    call_data_id             bigint   null,
    call_data                bytea    null,
    chain_id                 bytea    null,
    consensus_timestamp      bigint   not null,
    data                     bytea    not null,
    from_address             bytea    not null,
    gas_limit                bigint   not null,
    gas_price                bytea    null,
    hash                     bytea    not null,
    max_fee_per_gas          bytea    null,
    max_gas_allowance        bigint   not null,
    max_priority_fee_per_gas bytea    null,
    nonce                    bigint   not null,
    payer_account_id         bigint   not null,
    recovery_id              smallint null,
    signature_r              bytea    not null,
    signature_s              bytea    not null,
    signature_v              bytea    null,
    to_address               bytea    null,
    type                     smallint not null,
    value                    bytea    null,
    primary key (consensus_timestamp)
);

-- support conventional querying by transaction hash
create index if not exists ethereum_transaction__hash on ethereum_transaction (hash);

-- support additional sender details
alter table contract_result add column sender_id bigint null;
