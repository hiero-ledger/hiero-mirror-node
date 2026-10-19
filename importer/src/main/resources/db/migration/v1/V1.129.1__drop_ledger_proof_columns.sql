alter table if exists ledger
    add column if not exists timestamp_range int8range;

update ledger
set timestamp_range = int8range(consensus_timestamp, null);

alter table if exists ledger
    alter column timestamp_range set not null,
    drop column if exists consensus_timestamp,
    drop column if exists history_proof_verification_key;

create table if not exists ledger_history
(
    like ledger including defaults
);
comment on table ledger_history is 'Network ledger historical state';

create index if not exists ledger_history__ledger_id_lower_timestamp
    on ledger_history (ledger_id, lower(timestamp_range));
