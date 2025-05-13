-- HIP-336 Approval and Allowance API

-- transfers
alter table if exists crypto_transfer
    add column if not exists is_approval boolean null;

alter table if exists nft_transfer
    add column if not exists is_approval boolean null;

alter table if exists non_fee_transfer
    add column if not exists is_approval boolean null;

alter table if exists token_transfer
    add column if not exists is_approval boolean null;


-- crypto_allowance
create table if not exists crypto_allowance
(
    amount           bigint    not null,
    owner            bigint    not null,
    payer_account_id bigint    not null,
    spender          bigint    not null,
    timestamp_range  int8range not null,
    primary key (owner, spender)
);
comment on table crypto_allowance is 'Hbar allowances delegated by payer to spender';

create table if not exists crypto_allowance_history
(
    like crypto_allowance including defaults,
    primary key (owner, spender, timestamp_range)
);
comment on table crypto_allowance_history is 'History of hbar allowances delegated by payer to spender';

create index if not exists crypto_allowance_history__timestamp_range on crypto_allowance_history using gist (timestamp_range);


-- nft_allowance
create table if not exists nft_allowance
(
    approved_for_all boolean   not null,
    owner            bigint    not null,
    payer_account_id bigint    not null,
    serial_numbers   bigint[]  not null,
    spender          bigint    not null,
    timestamp_range  int8range not null,
    token_id         bigint    not null,
    primary key (owner, spender, token_id)
);
comment on table nft_allowance is 'NFT allowances delegated by payer to spender';

create table if not exists nft_allowance_history
(
    like nft_allowance including defaults,
    primary key (owner, spender, token_id, timestamp_range)
);
comment on table nft_allowance_history is 'History of NFT allowances delegated by payer to spender';

create index if not exists nft_allowance_history__timestamp_range on nft_allowance_history using gist (timestamp_range);


-- token_allowance
create table if not exists token_allowance
(
    amount           bigint    not null,
    owner            bigint    not null,
    payer_account_id bigint    not null,
    spender          bigint    not null,
    timestamp_range  int8range not null,
    token_id         bigint    not null,
    primary key (owner, spender, token_id)
);
comment on table token_allowance is 'Token allowances delegated by payer to spender';

create table if not exists token_allowance_history
(
    like token_allowance including defaults,
    primary key (owner, spender, token_id, timestamp_range)
);
comment on table token_allowance_history is 'History of token allowances delegated by payer to spender';

create index if not exists token_allowance_history__timestamp_range on token_allowance_history using gist (timestamp_range);
