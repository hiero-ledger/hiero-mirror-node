---
--- Add transaction bytes to t_transactions table
---
alter table t_transactions
    add column if not exists transaction_bytes bytea null;
alter table t_transactions
    alter column transaction_bytes set storage external;
