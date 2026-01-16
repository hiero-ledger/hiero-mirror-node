-------------------
-- Fix token total supply, nft transfer, and nft ownership for token dissociate of deleted tokens
-------------------

create index if not exists nft__account_token on nft (account_id, token_id);

-- create temp table and populate it with token transfers of token dissociate transactions
create temporary table if not exists token_dissociate_transfer on commit drop as table token_transfer limit 0;

insert into token_dissociate_transfer
select tt.*
from transaction t
join token_transfer tt on tt.consensus_timestamp = t.consensus_ns
where t.type = 41
order by t.consensus_ns;

-- update token total supply
update token t
set total_supply = t.total_supply + amount, modified_timestamp = consensus_timestamp
from token_dissociate_transfer
where token_dissociate_transfer.token_id = t.token_id;

-- mark still alive nfts involved in the transfer as deleted, update the timestamp, and return the nft info
with dissociated_nft as (
    update nft set deleted = true, modified_timestamp = tdt.consensus_timestamp
    from token_dissociate_transfer tdt
    where nft.token_id = tdt.token_id and nft.account_id = tdt.account_id and nft.deleted is false
    returning nft.token_id, nft.serial_number, nft.account_id, nft.modified_timestamp
),
-- for each dissociated nft, insert a nft transfer, and return the nft token id
updated_nft as (
    insert into nft_transfer (consensus_timestamp, sender_account_id, serial_number, token_id)
    select modified_timestamp, account_id, serial_number, token_id
    from dissociated_nft
    returning token_id
),
-- find the nft token transfers presented as token transfers
dissociate_nft_transfer as (
    select tdt.*
    from token_dissociate_transfer tdt
    join updated_nft un on un.token_id = tdt.token_id
)
-- delete the nft transfers from token_transfer table
delete from token_transfer tt using dissociate_nft_transfer dnt
where tt.consensus_timestamp = dnt.consensus_timestamp and
      tt.account_id = dnt.account_id and
      tt.amount = dnt.amount and
      tt.token_id = dnt.token_id;

-- drop the temp table
drop table if exists token_dissociate_transfer;

-- fix nft class total supply, nft, and add nft transfers for those without a token transfer from the token dissociate
-- find deleted nft classes
with deleted_nft_class as (
    select token_id, e.modified_timestamp
    from token t
    join entity e on e.id = t.token_id and e.deleted = true
    where t.type = 'NON_FUNGIBLE_UNIQUE'
),
-- the only change can be done to a token relationship of a deleted token is dissociate
dissociated_nft_relationship as (
    select ta.account_id, ta.modified_timestamp, ta.token_id
    from token_account ta
    join deleted_nft_class dnc on dnc.token_id = ta.token_id and ta.modified_timestamp > dnc.modified_timestamp
),
-- mark nfts as deleted, update its modified_timestamp, and return the nft info
dissociated_nft as (
    update nft set deleted = true, modified_timestamp = dnr.modified_timestamp
    from dissociated_nft_relationship dnr
    where nft.account_id = dnr.account_id and nft.token_id = dnr.token_id and nft.deleted is false
    returning nft.account_id, nft.token_id, nft.serial_number, nft.modified_timestamp
),
-- insert nft transfers
dissociated_nft_transfer as (
    insert into nft_transfer (consensus_timestamp, sender_account_id, serial_number, token_id)
    select modified_timestamp, account_id, serial_number, token_id
    from dissociated_nft
    returning consensus_timestamp, token_id
)
update token t
set
    total_supply = total_supply - 1,
    modified_timestamp = GREATEST(modified_timestamp, dnt.consensus_timestamp)
from dissociated_nft_transfer dnt
where dnt.token_id = t.token_id;

-- remove any nft transfer with the wildcard serial number (-1) which signifies an nft treasury update
delete from nft_transfer where serial_number = -1;
