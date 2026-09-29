-- Backfill contract.initcode from inline Ethereum calldata when the bytecode sidecar left it empty.
-- type 8 is CONTRACTCREATEINSTANCE and type 50 is ETHEREUMTRANSACTION.

with contract_create_calldata as materialized (
  select child.entity_id as id,
         child.consensus_timestamp as created_timestamp,
         et.call_data
  from ethereum_transaction et
  join transaction parent
    on parent.payer_account_id = et.payer_account_id
   and parent.consensus_timestamp = et.consensus_timestamp
   and parent.type = 50
  join transaction child
    on child.payer_account_id = parent.payer_account_id
   and child.parent_consensus_timestamp = parent.consensus_timestamp
   and child.type = 8
  where octet_length(et.call_data) > 0
)
update contract c
set initcode = contract_create_calldata.call_data
from contract_create_calldata
join entity e on e.id = contract_create_calldata.id
where c.id = e.id
  and e.created_timestamp = contract_create_calldata.created_timestamp
  and coalesce(octet_length(c.initcode), 0) = 0;
