-- Add index for results retrieval by
create index if not exists contract_result__id_payer_timestamp
    on contract_result (contract_id, payer_account_id, consensus_timestamp);
