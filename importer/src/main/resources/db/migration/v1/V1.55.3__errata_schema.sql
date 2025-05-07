-- Alter the schema for use with the Java errata migration

alter table if exists account_balance_file
    add column if not exists time_offset int default 0 not null;

create type errata_type as enum ('INSERT', 'DELETE');

alter table if exists crypto_transfer
    add column if not exists errata errata_type null;

alter table if exists transaction
    add column if not exists errata errata_type null;
