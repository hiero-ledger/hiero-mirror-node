-------------------
-- Update NFT tables based on discoveries adding Importer code
-------------------

-- Change nft primary key
alter table if exists nft
    drop constraint if exists nft_pkey;

drop index if exists nft__token_id_serial_num;

alter table nft
    add primary key (token_id, serial_number);

-- Remove not null constraints on nft created timestamp to allow for partial data flag
alter table nft alter column created_timestamp drop not null;
alter table nft alter column account_id drop not null;

-- Remove not null constraints on nft_tranfer account ids for mint/burn/wipe transfers
alter table nft_transfer alter column receiver_account_id drop not null;
alter table nft_transfer alter column sender_account_id drop not null;


-- Insert new response codes
insert into t_transaction_results (result, proto_id)
values ('METADATA_TOO_LONG', 227),
       ('BATCH_SIZE_LIMIT_EXCEEDED', 228),
       ('QUERY_RANGE_LIMIT_EXCEEDED', 229);

