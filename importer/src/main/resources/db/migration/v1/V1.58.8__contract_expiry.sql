alter table if exists contract
  add column if not exists auto_renew_account_id bigint null,
  add column if not exists permanent_removal boolean null;
alter table if exists contract_history
  add column if not exists auto_renew_account_id bigint null,
  add column if not exists permanent_removal boolean null;

update contract set permanent_removal = false where deleted is true;
