create domain entity_id as bigint;

create function encodeEntityId(shard bigint, realm bigint, num bigint)
returns entity_id as $$
begin
    -- Encoding: 15 bits for shard (mask = 0x7fff = 32767), followed by 16 bits for realm (mask = 0xffff = 65535),
    -- followed by 32 bits for num (max = 0xffffffff = 4294967295)
    return (num & 4294967295) | ((realm & 65535) << 32) | ((shard & 32767) << 48);
end
$$ language plpgsql;

-------------------
-- t_entities table
-------------------

alter table if exists t_entities
    add column if not exists encoded_id entity_id null,
    add column if not exists proxy_account_id entity_id null;

-- Compute encoded ids once. Simple udpate is okay since entities table is very small.
update t_entities e
set encoded_id = encodeEntityId(entity_shard, entity_realm, entity_num);

alter table if exists t_entities
    alter column encoded_id set not null;

-- Migrate auto renew accounts and proxy accounts
update t_entities e
set auto_renew_account_id = e2.encoded_id
from t_entities e2
where e.auto_renew_account_id = e2.id
  and e.auto_renew_account_id is not null;

update t_entities e
set proxy_account_id = e2.encoded_id
from t_entities e2
where e.fk_prox_acc_id = e2.id
  and e.fk_prox_acc_id is not null;

-------------------
-- transaction table
-------------------

create table if not exists transaction (
  consensus_ns bigint not null,
  type smallint not null,
  result smallint not null,
  payer_account_id entity_id not null ,
  valid_start_ns bigint not null,
  valid_duration_seconds bigint,
  node_account_id entity_id not null,
  entity_id entity_id null,
  initial_balance bigint default 0,
  max_fee hbar_tinybars,
  charged_tx_fee bigint,
  memo bytea,
  transaction_hash bytea,
  transaction_bytes bytea
);

-- Migrate data

insert into transaction
    (consensus_ns, type, result, payer_account_id, valid_start_ns, valid_duration_seconds, node_account_id, entity_id,
     initial_balance, max_fee, charged_tx_fee, memo, transaction_hash, transaction_bytes)
select
    consensus_ns, type, result, payer.encoded_id, valid_start_ns, valid_duration_seconds, node.encoded_id,
     e.encoded_id, initial_balance, max_fee, charged_tx_fee, t_transactions.memo, transaction_hash, transaction_bytes
from t_transactions
join t_entities node on t_transactions.fk_node_acc_id = node.id
join t_entities payer on t_transactions.fk_payer_acc_id = payer.id
left join t_entities e on t_transactions.fk_cud_entity_id = e.id;

-- Move table and recreate indexes

drop index if exists idx_t_transactions_node_account; -- drop explicitly for history since it is not re-created
drop table if exists t_transactions;

alter table transaction
    add primary key (consensus_ns);
create index transaction__transaction_id
    on transaction (valid_start_ns, payer_account_id);
create index transaction__payer_account_id
    on transaction (payer_account_id);

-------------------
-- t_entities table
-------------------

-- Drop indexes, columns and sequence

alter table if exists t_entities drop constraint t_entities_pkey;
drop index if exists idx_t_entities_id_num_id; -- useless index

alter table if exists t_entities
    drop column if exists id,
    drop column if exists fk_prox_acc_id;

drop sequence if exists s_entities_seq;

alter table if exists t_entities
    rename column encoded_id to id;

alter table if exists t_entities
    add primary key (id);
