-- Backfill contract.initcode from inline Ethereum calldata when the bytecode sidecar left it empty.
-- type 8 is CONTRACTCREATEINSTANCE and type 50 is ETHEREUMTRANSACTION.

update contract c
set initcode = et.call_data
from entity e
join transaction child
  on child.consensus_timestamp = e.created_timestamp
 and child.entity_id = e.id
 and child.type = 8
join transaction parent
  on parent.consensus_timestamp = child.parent_consensus_timestamp
 and parent.type = 50
join ethereum_transaction et
  on et.consensus_timestamp = parent.consensus_timestamp
where c.id = e.id
  and coalesce(octet_length(c.initcode), 0) = 0
  and octet_length(et.call_data) > 0;
