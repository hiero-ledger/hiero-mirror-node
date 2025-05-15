alter table if exists token
  add column freeze_status smallint,
  add column kyc_status smallint;

update token
  set freeze_status = case when freeze_key is null then 0
                           when freeze_default then 1
                           else 2
                      end,
      kyc_status = case when kyc_key is null then 0 else 2 end;

alter table if exists token
  alter column freeze_status set not null,
  alter column kyc_status set not null;

alter table if exists token_history
  add column freeze_status smallint,
  add column kyc_status smallint;

update token_history
  set freeze_status = case when freeze_key is null then 0
                           when freeze_default then 1
                           else 2
                      end,
      kyc_status = case when kyc_key is null then 0 else 2 end;

alter table if exists token_history
  alter column freeze_status set not null,
  alter column kyc_status set not null;

alter table if exists token_account
  alter column automatic_association drop default,
  alter column automatic_association drop not null,
  alter column balance_timestamp drop not null,
  alter column created_timestamp drop not null,
  alter column freeze_status drop default,
  alter column freeze_status drop not null,
  alter column kyc_status drop default,
  alter column kyc_status drop not null;


alter table if exists token_account_history
  alter column automatic_association drop default,
  alter column automatic_association drop not null,
  alter column balance_timestamp drop not null,
  alter column created_timestamp drop not null,
  alter column freeze_status drop default,
  alter column freeze_status drop not null,
  alter column kyc_status drop default,
  alter column kyc_status drop not null;
