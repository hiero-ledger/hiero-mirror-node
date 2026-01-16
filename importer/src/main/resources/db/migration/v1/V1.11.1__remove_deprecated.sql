drop table if exists t_account_balances__deprecated cascade;
drop table if exists t_account_balance_history__deprecated;
drop table if exists t_account_balance_refresh_time__deprecated;

alter table account_balances
    drop column if exists account_id__deprecated;
-- OPS NOTE: either a vacuum analyze or vacuum full (careful) should be done on the table after this removal.

drop type if exists entity_id;
