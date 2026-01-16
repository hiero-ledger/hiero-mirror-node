-------------------
-- Add new response codes
-------------------

insert into t_transaction_results (result, proto_id)
values ('INVALID_TOKEN_MINT_METADATA', 248),
       ('INVALID_TOKEN_BURN_METADATA', 249),
       ('CURRENT_TREASURY_STILL_OWNS_NFTS', 250);
