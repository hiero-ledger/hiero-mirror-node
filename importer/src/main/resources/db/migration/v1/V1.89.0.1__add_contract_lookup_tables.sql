create table if not exists contract_transaction_hash
(
    consensus_timestamp bigint   not null,
    hash                bytea    not null,
    payer_account_id    bigint   not null,
    entity_id           bigint   not null,
    transaction_result  smallint not null
);
comment on table contract_transaction_hash is 'First 32 bytes of network transaction hash (or ethereum hash) to transaction details mapping';

insert into contract_transaction_hash(consensus_timestamp,hash,payer_account_id,entity_id, transaction_result)
    (select  cr.consensus_timestamp, cr.transaction_hash, cr.payer_account_id, cr.contract_id, cr.transaction_result
     from contract_result cr where cr.transaction_hash is not null);

insert into contract_transaction_hash(consensus_timestamp,hash,payer_account_id,entity_id, transaction_result)
    (select et.consensus_timestamp, et.hash, et.payer_account_id, t.payer_account_id, t.result
     from ethereum_transaction et
     join transaction t on t.consensus_timestamp = et.consensus_timestamp
     where not exists (select 1 from contract_result cr where cr.consensus_timestamp = et.consensus_timestamp));

create index if not exists contract_transaction_hash__hash
    on contract_transaction_hash using hash (hash);

drop index if exists ethereum_transaction__hash;
drop index if exists contract_result__hash;

create table if not exists contract_transaction
(
    consensus_timestamp bigint       not null,
    entity_id           bigint       not null,
    contract_ids        bigint array not null,
    payer_account_id    bigint       not null
);
comment on table contract_transaction is 'Maps contract parties to contract transaction details for a given timestamp';

alter table if exists contract_transaction
    add constraint contract_transaction__pk primary key (consensus_timestamp, entity_id);

with involved_contracts as
         (select cr.consensus_timestamp,
                 array(
                     select distinct e
                        from unnest(
                                array_remove(
                                    array_agg(cl.contract_id) ||
                                    array_agg(csc.contract_id) ||
                                    array_agg(cr.contract_id) || cr.payer_account_id,
                                    NULL)) as e
                        ) as contract_ids,
                 cr.payer_account_id
          from contract_result cr
                   left join contract_log cl on cl.consensus_timestamp = cr.consensus_timestamp
                   left join contract_state_change csc
                             on csc.consensus_timestamp = cr.consensus_timestamp
          group by cr.consensus_timestamp, cr.payer_account_id)
insert into contract_transaction(consensus_timestamp,entity_id,contract_ids, payer_account_id)
(select consensus_timestamp, unnest(contract_ids), contract_ids, payer_account_id from involved_contracts);