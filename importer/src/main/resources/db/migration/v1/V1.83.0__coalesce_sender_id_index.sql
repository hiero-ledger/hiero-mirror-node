drop index if exists contract_result__id_payer_timestamp;
drop index if exists contract_result__payer_timestamp;

update contract_result
set sender_id = coalesce(sender_id, payer_account_id);

create index if not exists contract_result__id_sender_timestamp
  on contract_result (contract_id, sender_id, consensus_timestamp);
create index if not exists contract_result__sender_timestamp
  on contract_result (sender_id, consensus_timestamp);