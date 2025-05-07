-- copy gas_limit from contract_result to ethereum_transaction
update ethereum_transaction et
set gas_limit = cr.gas_limit
from contract_result cr
where et.consensus_timestamp = cr.consensus_timestamp;
