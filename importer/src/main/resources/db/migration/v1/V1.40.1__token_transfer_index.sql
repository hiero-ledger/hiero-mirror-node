-------------------
-- Change the index on token_transfer (account) to (account, consensus_timestamp) to speedup the query to get a list of
-- consensus timestamps of token transfers for specific account(s).
-------------------

drop index if exists token_transfer_account;
create index if not exists token_transfer__account_timestamp
    on token_transfer (account_id, consensus_timestamp desc);
