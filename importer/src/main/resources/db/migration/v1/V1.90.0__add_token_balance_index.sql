-- add an index to speed up the query for a specific token's balance distribution
create index if not exists token_balance__token_account_timestamp
    on token_balance (token_id, account_id, consensus_timestamp);
