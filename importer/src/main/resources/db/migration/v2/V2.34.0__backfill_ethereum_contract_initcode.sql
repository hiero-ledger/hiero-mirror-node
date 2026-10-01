-- Backfill contract.initcode from inline Ethereum calldata when the bytecode sidecar left it empty.
-- type 8 is CONTRACTCREATEINSTANCE and type 50 is ETHEREUMTRANSACTION.

create temp table missing_contract_ids on commit drop as
select c.id
from contract c
where octet_length(c.initcode) = 0;

create temp table contract_initcode_backfill on commit drop as
select missing.id,
       et.call_data
from missing_contract_ids missing
join transaction child
  on child.entity_id = missing.id
 and child.type = 8
join transaction parent
  on parent.payer_account_id = child.payer_account_id
 and parent.consensus_timestamp = child.parent_consensus_timestamp
 and parent.type = 50
join ethereum_transaction et
  on et.payer_account_id = parent.payer_account_id
 and et.consensus_timestamp = parent.consensus_timestamp
where octet_length(et.call_data) > 0;

update contract c
set initcode = backfill.call_data,
    file_id = null
from contract_initcode_backfill backfill
where c.id = backfill.id;
