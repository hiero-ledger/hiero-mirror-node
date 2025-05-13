-------------------
-- Add an index on the token transfer to optimize the accounts transaction query
-------------------

create index if not exists token_transfer_account
    on token_transfer (account_id);
