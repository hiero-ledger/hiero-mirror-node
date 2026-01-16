-- Fixes up the smart contract tables after normalizing the contract result raw protobuf into discrete columns

alter table if exists contract
    add column if not exists parent_id bigint null,
    alter column memo set default '';

alter table if exists contract_history
    add column if not exists parent_id bigint null,
    alter column memo set default '',
    alter column type set default 2;

alter table if exists entity_history
    add column if not exists parent_id bigint null,
    alter column memo set default '';

create temp table if not exists contract_transaction on commit drop as
select consensus_timestamp, entity_id, initial_balance, type
from transaction
where entity_id is not null
  and type in (7, 8, 9, 22);

-- Update the contract_result.amount from the contract call to clear the previous erroneous value from the last migration.
update contract_result cr
set amount = null
from contract_transaction ct
where cr.consensus_timestamp = ct.consensus_timestamp
  and ct.type = 7;

-- Update the contract_result.amount from the contract create transaction.initial_balance.
update contract_result cr
set amount = ct.initial_balance
from contract_transaction ct
where cr.consensus_timestamp = ct.consensus_timestamp
  and ct.type = 8;

-- Update the contract_result.contract_id from transaction.entity_id since it wasn't always populated correctly by main nodes
update contract_result cr
set contract_id = ct.entity_id
from contract_transaction ct
where cr.consensus_timestamp = ct.consensus_timestamp
  and ct.entity_id is not null
  and (cr.contract_id is null or cr.contract_id <= 0);

-- Create a map of parent contract IDs to created child contract IDs. In some HAPI versions, created_contract_ids can
-- also contain the parent so we exclude self-references.
create temporary table if not exists contract_relationship on commit drop as
select consensus_timestamp, contract_id as parent_contract_id, created_contract_ids[i] as child_contract_id
from (
         select consensus_timestamp,
                contract_id,
                created_contract_ids,
                generate_subscripts(created_contract_ids, 1) as i
         from contract_result
         where array_length(created_contract_ids, 1) > 0
           and contract_id is not null
     ) children
where contract_id <> created_contract_ids[i];

-- Move child contract IDs still marked as accounts in entity table to the contract table
with deleted_entity as (
    delete from entity e using contract_relationship cr where e.id = cr.child_contract_id returning *
)
insert
into contract (auto_renew_period,
               created_timestamp,
               deleted,
               expiration_timestamp,
               id,
               key,
               memo,
               num,
               proxy_account_id,
               public_key,
               realm,
               shard,
               timestamp_range)
select auto_renew_period,
       created_timestamp,
       deleted,
       expiration_timestamp,
       id,
       key,
       memo,
       num,
       proxy_account_id,
       public_key,
       realm,
       shard,
       timestamp_range
from deleted_entity;

-- Upsert the child contract. If the child ID doesn't exist copy the fields from the parent. If it does, update using
-- the merged parent and child fields.
insert
into contract (auto_renew_period,
               created_timestamp,
               deleted,
               expiration_timestamp,
               file_id,
               id,
               key,
               memo,
               num,
               obtainer_id,
               parent_id,
               proxy_account_id,
               public_key,
               realm,
               shard,
               timestamp_range,
               type)
select coalesce(child.auto_renew_period, parent.auto_renew_period),
       coalesce(child.created_timestamp, cr.consensus_timestamp),
       coalesce(child.deleted, parent.deleted),
       coalesce(child.expiration_timestamp, parent.expiration_timestamp),
       coalesce(child.file_id, parent.file_id),
       cr.child_contract_id,
       coalesce(child.key, parent.key),
       coalesce(child.memo, parent.memo, ''),
       cr.child_contract_id & 4294967295, -- Extract the num from the last 32 bits of the entity id
       coalesce(child.obtainer_id, parent.obtainer_id),
       cr.parent_contract_id,
       coalesce(child.proxy_account_id, parent.proxy_account_id),
       coalesce(child.public_key, parent.public_key),
       coalesce(child.realm, parent.realm, 0),
       coalesce(child.shard, parent.shard, 0),
       case
           when lower(child.timestamp_range) > 0 then child.timestamp_range
           else int8range(cr.consensus_timestamp, null) end,
       coalesce(parent.type, 2)
from contract_relationship cr
         left join contract child on child.id = cr.child_contract_id
         left join contract parent on parent.id = cr.parent_contract_id
on conflict (id)
    do update set auto_renew_period    = excluded.auto_renew_period,
                  created_timestamp    = excluded.created_timestamp,
                  deleted              = excluded.deleted,
                  expiration_timestamp = excluded.expiration_timestamp,
                  file_id              = excluded.file_id,
                  key                  = excluded.key,
                  memo                 = excluded.memo,
                  num                  = excluded.num,
                  obtainer_id          = excluded.obtainer_id,
                  parent_id            = excluded.parent_id,
                  proxy_account_id     = excluded.proxy_account_id,
                  public_key           = excluded.public_key,
                  realm                = excluded.realm,
                  shard                = excluded.shard,
                  timestamp_range      = excluded.timestamp_range,
                  type                 = excluded.type;
