-- Backfilled asynchronously by BackfillContractChildMigration.
alter table if exists contract_result
    add column if not exists child boolean default false not null;

alter table if exists contract_log
    add column if not exists child boolean default false not null;
