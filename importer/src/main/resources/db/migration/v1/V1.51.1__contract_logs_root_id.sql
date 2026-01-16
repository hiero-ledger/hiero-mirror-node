-- Add root_contract_id column to contract_log
alter table contract_log
    add column root_contract_id bigint;

-- Backfill root_contract_id based on timestamp
update contract_log
set root_contract_id = contract_result.contract_id
from contract_result
where contract_log.consensus_timestamp = contract_result.consensus_timestamp;

-- Create index on contract_id and timestamp for contract logs REST API
create index if not exists contract_log__contract_id_timestamp_index
    on contract_log (contract_id, consensus_timestamp desc, index);
