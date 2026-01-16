-------------------
-- Add new response codes
-------------------

insert into t_transaction_results (result, proto_id)
values ('MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED', 255),
       ('PAYER_ACCOUNT_DELETED', 256),
       ('CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH', 257),
       ('CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS', 258);
