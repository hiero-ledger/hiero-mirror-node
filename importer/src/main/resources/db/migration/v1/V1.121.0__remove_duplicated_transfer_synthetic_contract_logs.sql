delete from contract_log cl_dup
where cl_dup.consensus_timestamp > 177231600000000000
  and cl_dup.topic0 = '\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
  and exists (
    select 1
    from contract_log cl_orig
    where cl_orig.consensus_timestamp = cl_dup.consensus_timestamp
      and cl_orig.topic0 = '\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
      and cl_orig.data = cl_dup.data
      and cl_orig.topic1 is not distinct from cl_dup.topic1
      and cl_orig.topic2 is not distinct from cl_dup.topic2
      and cl_orig.topic3 is not distinct from cl_dup.topic3
      and cl_orig.transaction_hash != cl_dup.transaction_hash
      and cl_orig.transaction_index != cl_dup.transaction_index
      and cl_orig.index < cl_dup.index
  );