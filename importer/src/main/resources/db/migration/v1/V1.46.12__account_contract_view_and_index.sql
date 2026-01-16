-- create a view for accounts and contracts
create or replace view account_contract as
  select
    auto_renew_period,
    created_timestamp,
    deleted,
    expiration_timestamp,
    id,
    key,
    max_automatic_token_associations,
    memo,
    num,
    public_key,
    proxy_account_id,
    realm,
    shard,
    timestamp_range,
    type,
    receiver_sig_required
  from entity
  where type = 1
  union all
  select
    auto_renew_period,
    created_timestamp,
    deleted,
    expiration_timestamp,
    id,
    key,
    null as max_automatic_token_associations,
    memo,
    num,
    public_key,
    proxy_account_id,
    realm,
    shard,
    timestamp_range,
    type,
    null as receiver_sig_required
  from contract;

-- add the public key index for contract table
create index if not exists contract__public_key
    on contract (public_key) where public_key is not null;
