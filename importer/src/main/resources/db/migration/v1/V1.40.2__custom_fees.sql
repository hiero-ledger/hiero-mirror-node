-------------------
-- Update and add tables for custom fees support
-------------------

-- Insert new response codes
insert into t_transaction_results (result, proto_id)
values ('FRACTION_DIVIDES_BY_ZERO', 230),
       ('INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE', 231),
       ('CUSTOM_FEES_LIST_TOO_LONG', 232),
       ('INVALID_CUSTOM_FEE_COLLECTOR', 233),
       ('INVALID_TOKEN_ID_IN_CUSTOM_FEES', 234),
       ('TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR', 235),
       ('TOKEN_MAX_SUPPLY_REACHED', 236),
       ('SENDER_DOES_NOT_OWN_NFT_SERIAL_NO', 237),
       ('CUSTOM_FEE_NOT_FULLY_SPECIFIED', 238),
       ('CUSTOM_FEE_MUST_BE_POSITIVE', 239),
       ('TOKEN_HAS_NO_FEE_SCHEDULE_KEY', 240),
       ('CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE', 241),
       ('INVALID_CUSTOM_FRACTIONAL_FEES_SUM', 242),
       ('FRACTIONAL_FEE_MAX_AMOUNT_LESS_THAN_MIN_AMOUNT', 243),
       ('CUSTOM_SCHEDULE_ALREADY_HAS_NO_FEES', 244),
       ('CUSTOM_FEE_DENOMINATION_MUST_BE_FUNGIBLE_COMMON', 245),
       ('CUSTOM_FRACTIONAL_FEE_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON', 246),
       ('INVALID_CUSTOM_FEE_SCHEDULE_KEY', 247);

-- Add the new transaction type
insert into t_transaction_types (proto_id, name, entity_type) values (45, 'TOKENFEESCHEDULEUPDATE', 5);

-- Add new columns to token
alter table if exists token
    add column if not exists fee_schedule_key bytea,
    add column if not exists fee_schedule_key_ed25519_hex varchar;

-- Add table assessed_custom_fee
create table if not exists assessed_custom_fee (
    amount               bigint not null,
    collector_account_id bigint not null,
    consensus_timestamp  bigint not null,
    token_id             bigint
);
create index if not exists assessed_custom_fee__consensus_timestamp
    on assessed_custom_fee (consensus_timestamp);
comment on table assessed_custom_fee is 'Assessed custom fees for HTS transactions';

-- Add table custom_fee
create table if not exists custom_fee
(
    amount                bigint,
    amount_denominator    bigint,
    collector_account_id  bigint,
    created_timestamp     bigint not null,
    denominating_token_id bigint,
    maximum_amount        bigint,
    minimum_amount        bigint not null default 0,
    token_id              bigint not null
);
create index if not exists custom_fee__token_timestamp
    on custom_fee (token_id desc, created_timestamp desc);
comment on table custom_fee is 'HTS Custom fees';

-- Add the default custom fee for existing tokens
insert into custom_fee (created_timestamp, token_id)
select created_timestamp, token_id from token;
