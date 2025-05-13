-- backfill default contract result for failed ethereum transactions
insert into contract_result (call_result, consensus_timestamp, contract_id, function_parameters, gas_limit, gas_used, payer_account_id, transaction_hash, transaction_index, transaction_nonce, transaction_result)
select '\x', et.consensus_timestamp, 0, coalesce(et.call_data, '\x'), et.gas_limit, 0, et.payer_account_id, et.hash, t.index, t.nonce, t.result
from ethereum_transaction et
join transaction t on t.consensus_timestamp = et.consensus_timestamp
left join contract_result cr on cr.consensus_timestamp = et.consensus_timestamp
where cr.consensus_timestamp is null and t.result not in (11, 22, 104, 220, 312);
