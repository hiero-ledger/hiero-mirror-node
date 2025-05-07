-------------------
-- Support hts transactions
-------------------

-- Add new token entity type
insert into t_entity_types (id, name) values (5, 'token');

-- Add new hts transaction body types
insert into t_transaction_types (proto_id, name) values
    (28, 'UNCHECKEDSUBMIT'),
    (29, 'TOKENCREATION'),
    (30, 'TOKENTRANSFERS'),
    (31, 'TOKENFREEZE'),
    (32, 'TOKENUNFREEZE'),
    (33, 'TOKENGRANTKYC'),
    (34, 'TOKENREVOKEKYC'),
    (35, 'TOKENDELETION'),
    (36, 'TOKENUPDATE'),
    (37, 'TOKENMINT'),
    (38, 'TOKENBURN'),
    (39, 'TOKENWIPE'),
    (40, 'TOKENASSOCIATE'),
    (41, 'TOKENDISSOCIATE');

-- Add hts transaction result types
insert into t_transaction_results (proto_id, result) values
    (165, 'ACCOUNT_FROZEN_FOR_TOKEN'),
    (166, 'TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED'),
    (167, 'INVALID_TOKEN_ID'),
    (168, 'INVALID_TOKEN_DECIMALS'),
    (169, 'INVALID_TOKEN_INITIAL_SUPPLY'),
    (170, 'INVALID_TREASURY_ACCOUNT_FOR_TOKEN'),
    (171, 'INVALID_TOKEN_SYMBOL'),
    (172, 'TOKEN_HAS_NO_FREEZE_KEY'),
    (173, 'TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN'),
    (174, 'MISSING_TOKEN_SYMBOL'),
    (175, 'TOKEN_SYMBOL_TOO_LONG'),
    (176, 'ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN'),
    (177, 'TOKEN_HAS_NO_KYC_KEY'),
    (178, 'INSUFFICIENT_TOKEN_BALANCE'),
    (179, 'TOKEN_WAS_DELETED'),
    (180, 'TOKEN_HAS_NO_SUPPLY_KEY'),
    (181, 'TOKEN_HAS_NO_WIPE_KEY'),
    (182, 'INVALID_TOKEN_MINT_AMOUNT'),
    (183, 'INVALID_TOKEN_BURN_AMOUNT'),
    (184, 'TOKEN_NOT_ASSOCIATED_TO_ACCOUNT'),
    (185, 'CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT'),
    (186, 'INVALID_KYC_KEY'),
    (187, 'INVALID_WIPE_KEY'),
    (188, 'INVALID_FREEZE_KEY'),
    (189, 'INVALID_SUPPLY_KEY'),
    (190, 'MISSING_TOKEN_NAME'),
    (191, 'TOKEN_NAME_TOO_LONG'),
    (192, 'INVALID_WIPING_AMOUNT'),
    (193, 'TOKEN_IS_IMMUTABLE'),
    (194, 'TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT'),
    (195, 'TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES'),
    (196, 'ACCOUNT_IS_TREASURY');

-- Add token table to hold token properties
create table if not exists token
(
    token_id                entity_id               primary key,
    created_timestamp       bigint                  not null,
    decimals                bigint                  not null,
    freeze_default          boolean                 not null default false,
    freeze_key              bytea,
    freeze_key_ed25519_hex  varchar                 null,
    initial_supply          bigint                  not null,
    kyc_key                 bytea,
    kyc_key_ed25519_hex     varchar                 null,
    modified_timestamp      bigint                  not null,
    name                    character varying(100)  not null,
    supply_key              bytea,
    supply_key_ed25519_hex  varchar                 null,
    symbol                  character varying(32)   not null,
    total_supply            bigint                  not null default 0,
    treasury_account_id     entity_id               not null,
    wipe_key                bytea,
    wipe_key_ed25519_hex    varchar                 null
);

--- Add token_account table to capture token-account info such as frozen, kyc and wipe status
create table if not exists token_account
(
    id                  serial              primary key,
    account_id          entity_id           not null,
    associated          boolean             not null default false,
    created_timestamp   bigint              not null,
    freeze_status       smallint            not null default 0,
    kyc_status          smallint            not null default 0,
    modified_timestamp  bigint              not null,
    token_id            entity_id           not null
);

create unique index if not exists token_account__token_account on token_account(token_id, account_id);


--- Add token_balance table to capture token account balances
create table if not exists token_balance
(
    consensus_timestamp bigint              not null,
    account_id          entity_id           not null,
    balance             bigint              not null,
    token_id            entity_id           not null
);

alter table if exists token_balance
    add constraint token_balance__pk primary key (consensus_timestamp, account_id, token_id);

--- Add token_transfer
create table if not exists token_transfer
(
    token_id            entity_id       not null,
    account_id          entity_id       not null,
    consensus_timestamp bigint          not null,
    amount              hbar_tinybars   not null
);

create index if not exists token_transfer__token_account_timestamp
     on token_transfer (consensus_timestamp desc, token_id desc, account_id desc);
