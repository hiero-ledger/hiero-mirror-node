-------------------
-- HIP-336 Track remaining crypto and fungible token allowances
-------------------

-- Alter crypto_allowance
alter table if exists crypto_allowance
    rename column amount to amount_granted;
alter table if exists crypto_allowance
    add column if not exists amount bigint not null default 0;

-- Alter crypto_allowance_history
alter table if exists crypto_allowance_history
    rename column amount to amount_granted;
alter table if exists crypto_allowance_history
    add column if not exists amount bigint not null default 0;

-- Backfill crypto_allowance.amount
update crypto_allowance set amount = amount_granted;

-- Alter token_allowance
alter table if exists token_allowance
    rename column amount to amount_granted;
alter table if exists token_allowance
    add column if not exists amount bigint not null default 0;

-- Alter token_allowance_history
alter table if exists token_allowance_history
    rename column amount to amount_granted;
alter table if exists token_allowance_history
    add column if not exists amount bigint not null default 0;

-- Backfill token_allowance.amount
with spent_allowance as (select coalesce(sum(tt.amount), 0) as amount, ta.owner, ta.spender, ta.token_id
                         from token_allowance ta
                         left join token_transfer tt
                           on tt.account_id = ta.owner
                             and tt.payer_account_id = ta.spender
                             and tt.token_id = ta.token_id
                             and tt.amount < 0
                             and tt.is_approval is true
                             and tt.consensus_timestamp >= lower(ta.timestamp_range)
                         where ta.amount_granted > 0
                         group by ta.owner, ta.spender, ta.token_id)
update token_allowance ta
set amount = ta.amount_granted + a.amount
from spent_allowance a
where ta.owner = a.owner
  and ta.spender = a.spender
  and ta.token_id = a.token_id;
