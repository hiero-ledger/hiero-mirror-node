-------------------
-- Migrate from t_entities to entity
-- Update auto_renew_account_id, ed25519_public_key_hex, deleted, memo and proxy_account_id columns
-- Add created_timestamp and modified_timestamp and pull values from transaction table
-------------------

create table if not exists entity
(
    auto_renew_account_id bigint,
    auto_renew_period     bigint,
    created_timestamp     bigint,
    deleted               boolean default false not null,
    expiration_timestamp  bigint,
    id                    bigint                not null,
    key                   bytea,
    memo                  text    default ''    not null,
    modified_timestamp    bigint,
    num                   bigint                not null,
    public_key            character varying,
    proxy_account_id      bigint,
    realm                 bigint                not null,
    shard                 bigint                not null,
    submit_key            bytea,
    type                  integer               not null
);
comment on table entity is 'Network entity with state';

insert into entity (auto_renew_account_id, auto_renew_period, deleted, num, realm, shard, public_key,
                    expiration_timestamp, type, id, key, memo, proxy_account_id, submit_key)
select case when auto_renew_account_id = 0 then null else auto_renew_account_id end,
       auto_renew_period,
       deleted,
       entity_num,
       entity_realm,
       entity_shard,
       ed25519_public_key_hex,
       exp_time_ns,
       fk_entity_type_id,
       id,
       key,
       case when memo is null then '' else memo end,
       case when proxy_account_id = 0 then null else proxy_account_id end,
       submit_key
from t_entities
order by id asc;

-- drop table and associated indexes
drop table if exists t_entities cascade;

-- add indexes
alter table entity
    add primary key (id);
-- Enforce lowercase hex representation by constraint rather than making indexes on lower(ed25519).
alter table entity
    add constraint c__entity__lower_ed25519
        check (public_key = lower(public_key));
create index if not exists entity__public_key_natural_id
    on entity (public_key, type, shard, realm, num);
create unique index if not exists entity__shard_realm_num
    on entity (shard, realm, num);


-- add a transaction type index
create index if not exists transaction_type
    on transaction (type);

-- set created and modified timestamp based on entity creating or modifying transaction types
with entity__timestamp_map as (
    select entity_id, min(consensus_ns) as created_timestamp, max(consensus_ns) as modified_timestamp
    from transaction
    where result = 22
      and entity_id is not null
    group by entity_id
    order by entity_id, created_timestamp
)
update entity
set created_timestamp  = entity__timestamp_map.created_timestamp,
    modified_timestamp = entity__timestamp_map.modified_timestamp
from entity__timestamp_map
where id = entity__timestamp_map.entity_id;
