-- add columns to nft table for nft instance allowance
alter table nft
  add column if not exists allowance_granted_timestamp bigint default null,
  add column if not exists delegating_spender bigint default null,
  add column if not exists spender bigint default null;

alter table nft_allowance drop column if exists serial_numbers;
alter table nft_allowance_history drop column if exists serial_numbers;

truncate nft_allowance;
truncate nft_allowance_history;

-- create new index for nft instance allowance query
create index if not exists nft__allowance on nft (account_id, spender, token_id, serial_number)
  where account_id is not null and spender is not null;
