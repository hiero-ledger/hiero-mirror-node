-- add index to improve performance of spender centric nft allowance queries
create index if not exists nft_allowance__spender_owner_token on nft_allowance (spender, owner, token_id);
