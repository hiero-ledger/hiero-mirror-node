-------------------
-- Add scheduled to transaction table
-------------------

alter table if exists transaction
    add column if not exists scheduled boolean not null default false;
