--
-- Migrate off account_id as entity_id to separate columns for realm/num instead of user defined type.
--
-- 1. Remove indexes and PK on account_balances.
-- 2. Add new (nullable) columns for account_realm_num and account_num.
-- 3. Migrate data from account_id to account_realm_num, account_num.
-- 4. Make account_id nullable and rename to deprecated, to be removed in subsequent release (along with entity_id UDT)
-- 5. Make new columns non-nullable.
-- 6. Re-add PK and indexes.
--
alter table account_balances
    drop constraint pk__account_balances;
drop index idx__account_balances__account_then_timestamp;

alter table account_balances
    add column account_realm_num entity_realm_num null;
alter table account_balances
    add column account_num entity_num null;

update account_balances
    set account_realm_num = (account_id).realm_num,
        account_num = (account_id).num;

alter table account_balances
    alter column account_id drop not null;

alter table account_balances
    rename column account_id to account_id__deprecated;

alter table account_balances
    alter column account_realm_num set not null;
alter table account_balances
    alter column account_num set not null;

alter table account_balances
    add constraint pk__account_balances primary key (consensus_timestamp, account_realm_num, account_num);
create index idx__account_balances__account_then_timestamp
    on account_balances (account_realm_num desc, account_num desc, consensus_timestamp desc);

-- Mark old account balances tables as deprecated
alter table t_account_balances
    rename to t_account_balances__deprecated;
alter table t_account_balance_refresh_time
    rename to t_account_balance_refresh_time__deprecated;
alter table t_account_balance_history
    rename to t_account_balance_history__deprecated;
