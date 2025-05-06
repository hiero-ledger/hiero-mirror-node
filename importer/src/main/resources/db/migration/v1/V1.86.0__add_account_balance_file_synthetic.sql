alter table if exists account_balance_file
  add column synthetic boolean default false not null;
