-------------------
-- Add support for automatic token associations
-------------------

insert into t_transaction_results (result, proto_id)
values ('NO_REMAINING_AUTOMATIC_ASSOCIATIONS', 262),
       ('EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT', 263),
       ('REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT', 264);

alter table if exists entity
    add column max_automatic_token_associations integer;

update entity
set max_automatic_token_associations = 0
where type = 1;

alter table if exists token_account
    add column automatic_association boolean not null default false;

alter table if exists token_account
    drop constraint token_account_pkey,
    add primary key (account_id, token_id, modified_timestamp);
