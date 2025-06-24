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
     )
update contract_log cl
set
    topic1 = coalesce(e1.evm_address, cl.topic1),
    topic2 = coalesce(e2.evm_address, cl.topic2)
    from cl_ext
left join entity_addresses e1 on e1.num = (
    ('x' || (
    case when length(right(cl_ext.topic1_hex, 16)) % 2 = 1
    then '0' || right(cl_ext.topic1_hex, 16)
    else right(cl_ext.topic1_hex, 16)
    end
    ))::bit(64)::bigint
    )
    left join entity_addresses e2 on e2.num = (
    ('x' || (
    case when length(right(cl_ext.topic2_hex, 16)) % 2 = 1
    then '0' || right(cl_ext.topic2_hex, 16)
    else right(cl_ext.topic2_hex, 16)
    end
    ))::bit(64)::bigint
    )
where cl.contract_id = cl_ext.contract_id
  and cl.consensus_timestamp = cl_ext.consensus_timestamp
  and cl.index = cl_ext.index
  and cl_ext.topic0 = '\xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef'
  and (cl_ext.topic1_hex !~ '^0*$' or cl_ext.topic2_hex !~ '^0*$')
  and (
    (length(cl_ext.topic1_hex) <= 16 or left(cl_ext.topic1_hex, length(cl_ext.topic1_hex) - 16) ~ '^0*$') or
    (length(cl_ext.topic2_hex) <= 16 or left(cl_ext.topic2_hex, length(cl_ext.topic2_hex) - 16) ~ '^0*$')
    )
  and (e1.num is not null or e2.num is not null);
