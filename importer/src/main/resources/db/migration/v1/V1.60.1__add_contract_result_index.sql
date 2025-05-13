-------------------
-- Add indexes to contract_result to improve rest api query performance
-------------------

create index if not exists contract_result__id_timestamp
  on contract_result (contract_id, consensus_timestamp);
create index if not exists contract_result__payer_timestamp
  on contract_result (payer_account_id, consensus_timestamp);
