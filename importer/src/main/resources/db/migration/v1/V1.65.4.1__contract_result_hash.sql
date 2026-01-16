alter table if exists contract_result
    add column if not exists transaction_hash   bytea    null,
    add column if not exists transaction_index  integer  null,
    add column if not exists transaction_result smallint null;

with results as (
    select t.consensus_timestamp,
           coalesce(et.hash, substring(t.transaction_hash from 1 for 32)) as transaction_hash,
           t.index                                                        as transaction_index,
           t.result                                                       as transaction_result
    from contract_result cr
             left join transaction t on t.consensus_timestamp = cr.consensus_timestamp
             left join ethereum_transaction et on t.consensus_timestamp = et.consensus_timestamp
)
update contract_result
set transaction_hash   = r.transaction_hash,
    transaction_index  = r.transaction_index,
    transaction_result = r.transaction_result
from results r
where r.consensus_timestamp = contract_result.consensus_timestamp;

alter table if exists contract_result
    alter column transaction_result set not null;
