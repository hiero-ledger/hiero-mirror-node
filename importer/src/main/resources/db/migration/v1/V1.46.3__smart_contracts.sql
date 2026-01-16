-- Enhances smart contract storage to include all protobuf fields and to track changes to it over time

-- contract
create table if not exists contract
(
    like entity,
    file_id     bigint null,
    obtainer_id bigint null
);

alter table if exists contract
    add primary key (id);

create temp table if not exists contract_transaction on commit drop as
select consensus_ns, entity_id, payer_account_id, result, type
from transaction
where entity_id is not null
  and type in (7, 8, 9, 22);

-- Ensure entities associated with contract transactions are properly marked as contracts
with ct as (
    select distinct entity_id
    from contract_transaction
)
update entity e
set type = 2
from ct
where e.id = ct.entity_id
  and e.type <> 2;

-- Move contract entities into contract and delete from entity
with contract_entity as (
    delete from entity where type = 2 returning *
)
insert
into contract
select *
from contract_entity;

-- Populate the new contract.obtainer_id from the non-fee paying entity associated with a contract delete
with contract_delete as (
    select distinct ct.consensus_timestamp, ct.entity_id as obtainer_id, t.entity_id as contract_id
    from contract_transaction t
             left join crypto_transfer ct on ct.consensus_timestamp = t.consensus_ns
    where t.type = 22
      and t.result = 22
      and t.entity_id is not null
      and ct.entity_id not in (t.payer_account_id, t.entity_id)
      and ct.entity_id >= 100
)
update contract c
set obtainer_id = cd.obtainer_id
from contract_delete cd
where c.id = cd.contract_id;

-- Remove columns that don't apply to contracts
alter table contract
    drop column auto_renew_account_id,
    drop column max_automatic_token_associations,
    drop column receiver_sig_required,
    drop column submit_key,
    alter column type set default 2;


-- contract_history
create table if not exists contract_history
(
    like contract,
    primary key (id, timestamp_range)
);

create index if not exists contract_history__timestamp_range on contract_history using gist (timestamp_range);


-- contract_log
create table if not exists contract_log
(
    bloom               bytea       not null,
    consensus_timestamp bigint      not null,
    contract_id         bigint      not null,
    data                bytea       not null,
    index               int         not null,
    topic0              varchar(64) null,
    topic1              varchar(64) null,
    topic2              varchar(64) null,
    topic3              varchar(64) null,
    primary key (consensus_timestamp, index)
);


-- contract_result

-- Temporary column until a Java migration can parse the stored protobuf into its normalized fields
alter table if exists contract_result
    rename call_result to function_result;

alter table if exists contract_result
    rename gas_supplied to gas_limit;

alter table if exists contract_result
    add column if not exists amount               bigint       null,
    add column if not exists bloom                bytea        null,
    add column if not exists call_result          bytea        null,
    add column if not exists contract_id          bigint       null,
    add column if not exists created_contract_ids bigint array null,
    add column if not exists error_message        text         null,
    alter column function_parameters set not null,
    alter column gas_limit set not null,
    alter column gas_used set not null;

drop index if exists idx__t_contract_result__consensus;

alter table if exists contract_result
    add primary key (consensus_timestamp);

-- Populate the new contract_result.amount from the aggregated non-fee crypto transfers associated with contract create and call transactions
with contract_call as (
    select distinct ct.consensus_timestamp, sum(ct.amount) as amount
    from contract_transaction t
             left join crypto_transfer ct on ct.consensus_timestamp = t.consensus_ns
    where t.type in (7, 8)
      and (ct.entity_id = t.payer_account_id or ct.entity_id < 100)
    group by ct.consensus_timestamp
)
update contract_result cr
set amount = cc.amount * (-1)
from contract_call cc
where cr.consensus_timestamp = cc.consensus_timestamp;
