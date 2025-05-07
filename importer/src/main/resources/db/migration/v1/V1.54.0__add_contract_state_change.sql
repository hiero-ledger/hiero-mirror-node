-------------------
-- Add contract_state_change table to improve smart contract traceability
-------------------

create table if not exists contract_state_change
(
    consensus_timestamp bigint not null,
    contract_id         bigint not null,
    payer_account_id    bigint not null,
    slot                bytea  not null,
    value_read          bytea  not null,
    value_written       bytea  null,
    primary key (consensus_timestamp, contract_id, slot)
);
