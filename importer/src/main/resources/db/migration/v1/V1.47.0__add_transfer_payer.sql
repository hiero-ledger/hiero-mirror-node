-------------------
-- Add transaction payer to transfer tables
-------------------
-- add payer to transfer tables
alter table assessed_custom_fee
    add column if not exists payer_account_id bigint;

alter table contract_result
    add column if not exists payer_account_id bigint;

alter table contract_log
    add column if not exists payer_account_id bigint;

alter table crypto_transfer
    add column if not exists payer_account_id bigint;

alter table nft_transfer
    add column if not exists payer_account_id bigint;

alter table non_fee_transfer
    add column if not exists payer_account_id bigint;

alter table token_transfer
    add column if not exists payer_account_id bigint;

-- from transaction table, insert payer to transfer tables
update assessed_custom_fee acf
set payer_account_id = t.payer_account_id
from transaction t
where acf.consensus_timestamp = t.consensus_timestamp;

update contract_log cl
set payer_account_id = t.payer_account_id
from transaction t
where cl.consensus_timestamp = t.consensus_timestamp;

update contract_result cr
set payer_account_id = t.payer_account_id
from transaction t
where cr.consensus_timestamp = t.consensus_timestamp;

update crypto_transfer ct
set payer_account_id = t.payer_account_id
from transaction t
where ct.consensus_timestamp = t.consensus_timestamp;

update nft_transfer nt
set payer_account_id = t.payer_account_id
from transaction t
where nt.consensus_timestamp = t.consensus_timestamp;

update non_fee_transfer nft
set payer_account_id = t.payer_account_id
from transaction t
where nft.consensus_timestamp = t.consensus_timestamp;

update token_transfer tt
set payer_account_id = t.payer_account_id
from transaction t
where tt.consensus_timestamp = t.consensus_timestamp;

-- set no nulls
alter table assessed_custom_fee
    alter column payer_account_id set not null;
alter table contract_log
    alter column payer_account_id set not null;
alter table contract_result
    alter column payer_account_id set not null;
alter table crypto_transfer
    alter column payer_account_id set not null;
alter table nft_transfer
    alter column payer_account_id set not null;
alter table non_fee_transfer
    alter column payer_account_id set not null;
alter table token_transfer
    alter column payer_account_id set not null;
