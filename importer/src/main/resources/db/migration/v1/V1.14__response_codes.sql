insert into t_transaction_results (proto_id, result) values
  (111, 'MAX_GAS_LIMIT_EXCEEDED'),
  (112, 'MAX_FILE_SIZE_EXCEEDED')
on conflict do nothing;
