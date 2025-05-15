create temp table token_allowance_remaining_amount (
  amount   bigint not null,
  owner    bigint not null,
  spender  bigint not null,
  token_id bigint not null
) on commit drop;

with token_transfer_erc20_approved as (
  select tt.consensus_timestamp, tt.account_id, tt.token_id, cr.sender_id as spender, tt.payer_account_id as incorrect_spender
  from token_transfer tt
  join contract_result cr on cr.consensus_timestamp = tt.consensus_timestamp and cr.sender_id <> tt.payer_account_id
  where cr.sender_id is not null and tt.is_approval is true
), token_allowance_affected as (
  select distinct on (ta.owner, ta.spender, ta.token_id) ta.*
  from token_allowance ta
  join token_transfer_erc20_approved tt on
    ta.owner = tt.account_id and
    ta.spender in (tt.spender, tt.incorrect_spender) and
    ta.token_id = tt.token_id
  where tt.consensus_timestamp > lower(ta.timestamp_range)
), token_transfer_all_approved as (
  select tt.amount, tt.account_id as owner, coalesce(cr.sender_id, tt.payer_account_id) as spender, tt.token_id
  from token_transfer tt
  left join contract_result cr on cr.consensus_timestamp = tt.consensus_timestamp
  join token_allowance_affected ta on
    ta.owner = tt.account_id and
    ta.token_id = tt.token_id and
    ((cr.consensus_timestamp is not null and ta.spender = cr.sender_id) or
     (cr.consensus_timestamp is null and ta.spender = tt.payer_account_id))
  where tt.consensus_timestamp > lower(ta.timestamp_range) and is_approval is true
), aggregated_amount as (
  select owner, spender, token_id, sum(amount) as total
  from token_transfer_all_approved
  group by owner, spender, token_id
)
insert into token_allowance_remaining_amount (amount, owner, spender, token_id)
select greatest(0, (amount_granted + coalesce(total, 0))), ta.owner, ta.spender, ta.token_id
from token_allowance_affected ta
left join aggregated_amount a using (owner, spender, token_id);

alter table token_allowance_remaining_amount add primary key (owner, spender, token_id);

update token_allowance ta
set amount = r.amount
from token_allowance_remaining_amount r
where r.owner = ta.owner and r.spender = ta.spender and r.token_id = ta.token_id;
