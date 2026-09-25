-- Inserts missing contract_transaction entries with 0 entity_id when 0 is already present in
-- contract_ids but the corresponding fan-out row was never created. The timestamp
-- 1784869200000000000 (2026-07-24T00:00:00Z) is around when 0.159.2 was created and deployed and used to optimize
-- the performance by skipping rows that did not have the regression.

with missing as (
    select distinct on (ct.consensus_timestamp, e.entity_id)
        ct.consensus_timestamp,
        e.entity_id,
        ct.contract_ids,
        ct.payer_account_id
    from contract_transaction ct
             cross join lateral unnest(ct.contract_ids) as e(entity_id)
    where contract_ids @> array[0]::bigint[] and e.entity_id = 0 and ct.consensus_timestamp >= 1784869200000000000
    order by ct.consensus_timestamp, e.entity_id, ct.entity_id
)
insert into contract_transaction (consensus_timestamp, entity_id, contract_ids, payer_account_id)
select consensus_timestamp, entity_id, contract_ids, payer_account_id
from missing
on conflict do nothing;
