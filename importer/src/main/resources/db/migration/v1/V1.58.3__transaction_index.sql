-------------------
-- Add transaction block index
-------------------

alter table if exists transaction add column if not exists index integer null;
