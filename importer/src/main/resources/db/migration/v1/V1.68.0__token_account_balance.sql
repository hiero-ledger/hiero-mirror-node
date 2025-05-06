alter table if exists token_account add column if not exists balance bigint not null default 0;
alter table if exists token_account_history add column if not exists balance bigint not null default 0;