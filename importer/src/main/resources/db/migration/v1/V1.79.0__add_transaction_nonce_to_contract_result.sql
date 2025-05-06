-- copy nonce from transaction to contract_result
alter table if exists contract_result
    add column if not exists transaction_nonce integer default 0 not null;

update contract_result cr
set transaction_nonce = t.nonce
from transaction t
where cr.consensus_timestamp = t.consensus_timestamp;
