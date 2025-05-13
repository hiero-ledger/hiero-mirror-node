-------------------
-- Change token_account primary key
-------------------

-- Token create transaction can auto associate multiple fee collectors with the new token, so
-- (created_timestamp, token_id) is no longer unique
alter table if exists token_account
    drop constraint if exists token_account_pkey;

alter table if exists token_account
    add primary key (token_id, account_id);

drop index if exists token_account__token_account;
