-- Backfill contract.initcode from inline Ethereum calldata when the bytecode sidecar left it empty.
-- type 8 is CONTRACTCREATEINSTANCE and type 50 is ETHEREUMTRANSACTION.

with contracts_missing_initcode as materialized (
  select c.id, e.created_timestamp
  from contract c
  join entity e on e.id = c.id
  where octet_length(c.initcode) = 0
)
update contract c
set initcode = et.call_data,
    file_id = null
from contracts_missing_initcode missing
join transaction child
  on child.entity_id = missing.id
 and child.consensus_timestamp = missing.created_timestamp
 and child.type = 8
join transaction parent
  on parent.payer_account_id = child.payer_account_id
 and parent.consensus_timestamp = child.parent_consensus_timestamp
 and parent.type = 50
join ethereum_transaction et
  on et.payer_account_id = parent.payer_account_id
 and et.consensus_timestamp = parent.consensus_timestamp
where c.id = missing.id
  and octet_length(et.call_data) > 0;
