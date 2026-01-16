-------------------
-- Update NFT tables with upsert support
-------------------

-- Remove not null constraints on nft created timestamp to allow for partial data selection during updates
alter table nft
    alter column deleted drop not null,
    alter column metadata drop not null;

