-------------------
-- Add new response codes
-------------------

insert into t_transaction_results (result, proto_id)
values ('ACCOUNT_STILL_OWNS_NFTS', 251),
       ('TREASURY_MUST_OWN_BURNED_NFT', 252),
       ('ACCOUNT_DOES_NOT_OWN_WIPED_NFT', 253),
       ('ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON', 254);
