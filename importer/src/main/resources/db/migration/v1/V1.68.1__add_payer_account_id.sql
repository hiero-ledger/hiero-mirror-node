--- contract_action
alter table if exists contract_action
  add column if not exists payer_account_id bigint;

update contract_action ca
set payer_account_id = t.payer_account_id
from transaction t
where t.consensus_timestamp = ca.consensus_timestamp;

alter table if exists contract_action
  alter column payer_account_id set not null;

--- prng
alter table if exists prng
  add column if not exists payer_account_id bigint;

update prng p
set payer_account_id = t.payer_account_id
from transaction t
where t.consensus_timestamp = p.consensus_timestamp;

alter table if exists prng
  alter column payer_account_id set not null;