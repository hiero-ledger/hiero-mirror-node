set citus.max_intermediate_result_size = -1;

with entity_addresses as (
    select num, evm_address
    from entity
    where length(evm_address) > 0
),
cl_ext as (
    select *,
           encode(topic1, 'hex') as topic1_hex,
           encode(topic2, 'hex') as topic2_hex
    from contract_log
    where topic0 = '\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
      and (
          octet_length(topic1) <= 8 or
          octet_length(topic2) <= 8
      )
)
update contract_log cl
set topic1 = coalesce(e1.evm_address, cl.topic1),
    topic2 = coalesce(e2.evm_address, cl.topic2)
from cl_ext
left join entity_addresses e1
    on octet_length(cl_ext.topic1) <= 8
   and e1.num = (('x' || lpad(cl_ext.topic1_hex, 16, '0'))::bit(64)::bigint)
left join entity_addresses e2
    on octet_length(cl_ext.topic2) <= 8
   and e2.num = (('x' || lpad(cl_ext.topic2_hex, 16, '0'))::bit(64)::bigint)
where cl.contract_id = cl_ext.contract_id
  and cl.consensus_timestamp = cl_ext.consensus_timestamp
  and cl.index = cl_ext.index
  and (
      e1.num is not null or
      e2.num is not null
  );
