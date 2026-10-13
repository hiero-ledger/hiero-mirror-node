alter table if exists ledger
    add column if not exists timestamp_range int8range not null,
    drop column if exists consensus_timestamp,
    drop column if exists history_proof_verification_key,
    drop column if exists node_contributions;

create table if not exists ledger_history
(
    like ledger including defaults
);
comment on table ledger_history is 'Network ledger historical state';