insert into t_transaction_results (proto_id, result)
    values (106, 'MAX_CONTRACT_STORAGE_EXCEEDED')
    on conflict do nothing;

