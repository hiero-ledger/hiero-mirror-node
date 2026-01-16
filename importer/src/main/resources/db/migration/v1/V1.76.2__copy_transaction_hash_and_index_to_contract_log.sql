-- copy transaction_hash and transaction_index from contract_result to contract_log
alter table if exists contract_log
    add column if not exists transaction_hash   bytea    null,
    add column if not exists transaction_index  integer  null;

update contract_log cl
set transaction_hash   = cr.transaction_hash,
    transaction_index  = cr.transaction_index
from contract_result cr
where cl.consensus_timestamp = cr.consensus_timestamp;

-- contract_result's contract_id column should no longer be nullable
update contract_result set contract_id = 0 where contract_id is null;

alter table contract_result
    alter column contract_id set not null;
