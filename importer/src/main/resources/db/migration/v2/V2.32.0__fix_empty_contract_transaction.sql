-- Inserts missing contract_transaction entries with 0 entity_id caused by a change in 0.159.2. The timestamp
-- 1784869200000000000 (2026-07-24T00:00:00Z) is around when 0.159.2 was created and deployed and used to optimize
-- the performance by skipping rows that did not have the regression.

with cr as materialized (
    select consensus_timestamp, contract_id, payer_account_id
    from contract_result
    where contract_id = 0 and consensus_timestamp >= 1784869200000000000
),
updates as (
    update contract_transaction ct
    set contract_ids = 0 || ct.contract_ids
    from cr
    where cr.consensus_timestamp = ct.consensus_timestamp and ct.consensus_timestamp >= 1784869200000000000
          and not (contract_ids @> array[0]::bigint[])
)
insert into contract_transaction (consensus_timestamp, entity_id, contract_ids, payer_account_id)
select ct.consensus_timestamp, cr.contract_id, 0 || ct.contract_ids as contract_ids, cr.payer_account_id
from cr
         join contract_transaction ct
              on ct.consensus_timestamp = cr.consensus_timestamp and ct.entity_id = cr.payer_account_id
where ct.consensus_timestamp >= 1784869200000000000 and not (contract_ids @> array[0]::bigint[])
on conflict do nothing;
