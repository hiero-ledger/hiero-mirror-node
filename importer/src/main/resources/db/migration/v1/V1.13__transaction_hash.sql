---
--- Add the transaction_hash field
---
alter table t_transactions
    add column if not exists transaction_hash bytea null;
