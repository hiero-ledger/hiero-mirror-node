-- Backfills the remaining amount of crypto allowances that were spent on-chain through a smart contract.
set citus.max_intermediate_result_size = -1;
set citus.enable_repartition_joins to on;

create temp table crypto_allowance_contract_spend on commit drop as
select ca.owner, ca.spender, sum(ct.amount) as amount_spent
from crypto_allowance ca
join crypto_transfer ct
  on ct.entity_id = ca.owner
  and ct.consensus_timestamp > lower(ca.timestamp_range)
  and ct.is_approval is true
  and ct.amount < 0
-- the synthetic transfer carries a contract_result at its own timestamp whose sender_id is the spender
join contract_result cr
  on cr.consensus_timestamp = ct.consensus_timestamp
  and cr.sender_id = ca.spender
where ca.amount_granted > 0
  -- only debits the importer didn't already attribute to the spender (those would have payer = spender)
  and ct.payer_account_id <> cr.sender_id
group by ca.owner, ca.spender;

update crypto_allowance ca
set amount = greatest(ca.amount + m.amount_spent, 0)
from crypto_allowance_contract_spend m
where ca.owner = m.owner
  and ca.spender = m.spender
  and m.amount_spent <> 0;
