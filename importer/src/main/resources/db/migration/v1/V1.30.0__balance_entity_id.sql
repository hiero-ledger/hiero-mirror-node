-------------------
-- Update account_balance to use single entity_id column instead of account_realm_num and account_num
-------------------

-- Drop pk and index that used account_realm_num and account_num. Drop first to make migration quicker
alter table account_balance drop constraint pk__account_balances;
drop index if exists idx__account_balances__account_then_timestamp;

--  Add account_id entity_id column and set based on existing account_realm_num and account_num
alter table if exists account_balance
    add column account_id entity_id null;

update account_balance
    set account_id = encodeEntityId(0, account_realm_num, account_num);

alter table if exists account_balance
    alter column account_id set not null;

-- Drop account_realm_num and account_num
alter table if exists account_balance
    drop column if exists account_realm_num,
    drop column if exists account_num;

-- Add new pk and index using account_id
alter table account_balance
    add constraint account_balance__pk primary key (consensus_timestamp, account_id);

create index if not exists account_balance__account_timestamp
    on account_balance (account_id desc, consensus_timestamp desc);
