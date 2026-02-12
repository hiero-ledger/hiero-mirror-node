create table if not exists ledger
(
    consensus_timestamp            bigint not null,
    history_proof_verification_key bytea  not null,
    ledger_id                      bytea  primary key,
    node_contributions             jsonb  not null
);
comment on table ledger is 'Ledger information';
