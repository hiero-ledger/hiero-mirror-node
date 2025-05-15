-------------------
-- Init mirror node db, defining table schema
-- Supports mirror nodes migrated from v1.0
-------------------

-- Create enums for tables
create type entity_type as enum ('UNKNOWN', 'ACCOUNT', 'CONTRACT', 'FILE', 'TOPIC', 'TOKEN', 'SCHEDULE');
create type errata_type as enum ('INSERT', 'DELETE');
create type token_pause_status as enum ('NOT_APPLICABLE', 'PAUSED', 'UNPAUSED');
create type token_supply_type as enum ('INFINITE', 'FINITE');
create type token_type as enum ('FUNGIBLE_COMMON', 'NON_FUNGIBLE_UNIQUE');

-- account_balance
create table if not exists account_balance
(
    account_id          bigint not null,
    balance             bigint not null,
    consensus_timestamp bigint not null
) partition by range (consensus_timestamp);
comment on table account_balance is 'Account balances (historical) in tinybars at different consensus timestamps';

create table if not exists account_balance_file
(
    bytes               bytea                 null,
    consensus_timestamp bigint                not null,
    count               bigint                not null,
    file_hash           varchar(96)           null,
    load_end            bigint                not null,
    load_start          bigint                not null,
    name                varchar(250)          not null,
    node_id             bigint                not null,
    synthetic           boolean default false not null,
    time_offset         int     default 0     not null
);
comment on table account_balance_file is 'Account balances stream files';

-- address_book
create table if not exists address_book
(
    end_consensus_timestamp   bigint null,
    file_data                 bytea  not null,
    file_id                   bigint not null,
    node_count                int    null,
    start_consensus_timestamp bigint not null
);
comment on table address_book is 'Network address book files entries';

-- address_book_entry
create table if not exists address_book_entry
(
    consensus_timestamp bigint        not null,
    description         varchar(100)  null,
    memo                varchar(128)  null,
    node_account_id     bigint        not null,
    node_cert_hash      bytea         null,
    node_id             bigint        not null,
    public_key          varchar(1024) null,
    stake               bigint        null
);
comment on table address_book_entry is 'Network address book node entries';

-- address_book_service_endpoint
create table if not exists address_book_service_endpoint
(
    consensus_timestamp bigint             not null,
    ip_address_v4       varchar(15)        not null,
    node_id             bigint             not null,
    port                integer default -1 not null
);
comment on table address_book_service_endpoint is 'Network address book node service endpoints';

-- assessed_custom_fee
create table if not exists assessed_custom_fee
(
    amount                      bigint   not null,
    collector_account_id        bigint   not null,
    consensus_timestamp         bigint   not null,
    effective_payer_account_ids bigint[] not null,
    payer_account_id            bigint   not null,
    token_id                    bigint
) partition by range (consensus_timestamp);
comment on table assessed_custom_fee is 'Assessed custom fees for HTS transactions';

-- contract
create table if not exists contract
(
    file_id          bigint null,
    id               bigint not null,
    initcode         bytea  null,
    runtime_bytecode bytea  null
);
comment on table contract is 'Contract entity';

--contract_action
create table if not exists contract_action
(
    call_depth          integer                        not null,
    call_operation_type integer                        null,
    call_type           integer                        null,
    caller              bigint                         null,
    caller_type         entity_type default 'CONTRACT' null,
    consensus_timestamp bigint                         not null,
    gas                 bigint                         not null,
    gas_used            bigint                         not null,
    index               integer                        not null,
    input               bytea                          null,
    payer_account_id    bigint                         not null,
    recipient_account   bigint                         null,
    recipient_address   bytea                          null,
    recipient_contract  bigint                         null,
    result_data         bytea                          null,
    result_data_type    integer                        not null,
    value               bigint                         not null
) partition by range (consensus_timestamp);
comment on table contract_action is 'Contract action';

-- contract_log
create table if not exists contract_log
(
    bloom               bytea   not null,
    consensus_timestamp bigint  not null,
    contract_id         bigint  not null,
    data                bytea   not null,
    index               int     not null,
    payer_account_id    bigint  not null,
    root_contract_id    bigint  null,
    topic0              bytea   null,
    topic1              bytea   null,
    topic2              bytea   null,
    topic3              bytea   null,
    transaction_hash    bytea   null,
    transaction_index   integer null
) partition by range (consensus_timestamp);
comment on table contract_log is 'Contract execution result logs';

-- contract_result
create table if not exists contract_result
(
    amount               bigint       null,
    bloom                bytea        null,
    call_result          bytea        null,
    consensus_timestamp  bigint       not null,
    contract_id          bigint       not null,
    created_contract_ids bigint array null,
    error_message        text         null,
    failed_initcode      bytea        null,
    function_parameters  bytea        not null,
    function_result      bytea        null,
    gas_consumed         bigint       null,
    gas_limit            bigint       not null,
    gas_used             bigint       null,
    payer_account_id     bigint       not null,
    sender_id            bigint       null,
    transaction_hash     bytea        null,
    transaction_index    integer      null,
    transaction_nonce    integer      not null,
    transaction_result   smallint     not null
) partition by range (consensus_timestamp);
comment on table contract_result is 'Crypto contract execution results';

-- contract_state
create table if not exists contract_state
(
    contract_id        bigint not null,
    created_timestamp  bigint not null,
    modified_timestamp bigint not null,
    slot               bytea  not null,
    value              bytea  null
);
comment on table contract_state is 'Current contract state';

create table if not exists contract_state_change
(
    consensus_timestamp bigint  not null,
    contract_id         bigint  not null,
    migration           boolean not null default false,
    payer_account_id    bigint  not null,
    slot                bytea   not null,
    value_read          bytea   not null,
    value_written       bytea   null
) partition by range (consensus_timestamp);
comment on table contract_state_change is 'Contract execution state changes';

create table if not exists contract_transaction
(
    consensus_timestamp bigint       not null,
    entity_id           bigint       not null,
    contract_ids        bigint array not null,
    payer_account_id    bigint       not null
) partition by range (consensus_timestamp);
comment on table contract_transaction is 'Maps contract parties to contract transaction details for a given timestamp';

create table if not exists contract_transaction_hash
(
    consensus_timestamp bigint   not null,
    hash                bytea    not null,
    payer_account_id    bigint   not null,
    entity_id           bigint   not null,
    transaction_result  smallint not null
);
comment on table contract_transaction_hash is 'First 32 bytes of network transaction hash (or ethereum hash) to transaction details mapping';

create table if not exists crypto_allowance
(
    amount           bigint    not null,
    amount_granted   bigint    not null,
    owner            bigint    not null,
    payer_account_id bigint    not null,
    spender          bigint    not null,
    timestamp_range  int8range not null
);
comment on table crypto_allowance is 'Hbar allowances delegated by owner to spender';

create table if not exists crypto_allowance_history
(
    like crypto_allowance including defaults
);
comment on table crypto_allowance_history is 'History of hbar allowances delegated by payer to spender';

-- crypto_transfer
create table if not exists crypto_transfer
(
    amount              bigint      not null,
    consensus_timestamp bigint      not null,
    entity_id           bigint      not null,
    errata              errata_type null,
    is_approval         boolean     null,
    payer_account_id    bigint      not null
) partition by range (consensus_timestamp);
comment on table crypto_transfer is 'Crypto account Hbar transfers';

-- custom_fee
create table if not exists custom_fee
(
    fixed_fees        jsonb,
    fractional_fees   jsonb,
    royalty_fees      jsonb,
    timestamp_range   int8range not null,
    token_id          bigint    not null
);
comment on table custom_fee is 'HTS Custom fees';

create table if not exists custom_fee_history
(
    like custom_fee including defaults
);
comment on table custom_fee_history is 'HTS Custom fees history state';

-- entity
create table if not exists entity
(
    alias                            bytea                 null,
    auto_renew_account_id            bigint                null,
    auto_renew_period                bigint                null,
    balance                          bigint                null,
    balance_timestamp                bigint                null,
    created_timestamp                bigint                null,
    decline_reward                   boolean default false not null,
    deleted                          boolean               null,
    ethereum_nonce                   bigint  default 0     null,
    evm_address                      bytea                 null,
    expiration_timestamp             bigint                null,
    id                               bigint                not null,
    key                              bytea                 null,
    max_automatic_token_associations integer               null,
    memo                             text    default ''    not null,
    num                              bigint                not null,
    obtainer_id                      bigint                null,
    permanent_removal                boolean               null,
    proxy_account_id                 bigint                null,
    public_key                       character varying     null,
    realm                            bigint                not null,
    receiver_sig_required            boolean               null,
    shard                            bigint                not null,
    staked_account_id                bigint                null,
    staked_node_id                   bigint  default -1    null,
    stake_period_start               bigint  default -1    null,
    submit_key                       bytea                 null,
    timestamp_range                  int8range             not null,
    type                             entity_type           not null default 'UNKNOWN'
);
comment on table entity is 'Network entity with state';

create table if not exists entity_history
(
    like entity including defaults
);
comment on table entity_history is 'Network entity historical state';

create table if not exists entity_stake
(
    end_stake_period     bigint    not null,
    id                   bigint    not null,
    pending_reward       bigint    not null,
    staked_node_id_start bigint    not null,
    staked_to_me         bigint    not null,
    stake_total_start    bigint    not null,
    timestamp_range      int8range not null
);
comment on table entity_stake is 'Network entity stake state';

create table if not exists entity_stake_history
(
    like entity_stake including defaults
);
comment on table entity_stake_history is 'Network entity stake historical state';

create table if not exists entity_transaction
(
    consensus_timestamp bigint   not null,
    entity_id           bigint   not null,
    payer_account_id    bigint   not null,
    result              smallint not null,
    type                smallint not null
) partition by range (consensus_timestamp);
comment on table entity_transaction is 'Network entity transaction lookup table';

create table if not exists ethereum_transaction
(
    access_list              bytea    null,
    call_data_id             bigint   null,
    call_data                bytea    null,
    chain_id                 bytea    null,
    consensus_timestamp      bigint   not null,
    data                     bytea    not null,
    gas_limit                bigint   not null,
    gas_price                bytea    null,
    hash                     bytea    not null,
    max_fee_per_gas          bytea    null,
    max_gas_allowance        bigint   not null,
    max_priority_fee_per_gas bytea    null,
    nonce                    bigint   not null,
    payer_account_id         bigint   not null,
    recovery_id              smallint null,
    signature_r              bytea    not null,
    signature_s              bytea    not null,
    signature_v              bytea    null,
    to_address               bytea    null,
    type                     smallint not null,
    value                    bytea    null
) partition by range (consensus_timestamp);
comment on table ethereum_transaction is 'Ethereum transaction details';

-- file_data
create table if not exists file_data
(
    consensus_timestamp bigint   not null,
    entity_id           bigint   not null,
    file_data           bytea    null,
    transaction_type    smallint not null
) partition by range (consensus_timestamp);
comment on table file_data is 'File data entity entries';

-- live_hash
create table if not exists live_hash
(
    livehash            bytea,
    consensus_timestamp bigint not null
);

create table if not exists network_freeze
(
    consensus_timestamp bigint   not null,
    end_time            bigint,
    file_hash           bytea    not null,
    file_id             bigint,
    payer_account_id    bigint   not null,
    start_time          bigint   not null,
    type                smallint not null
) partition by range (consensus_timestamp);
comment on table network_freeze is 'System transaction to freeze the network';

create table if not exists network_stake
(
    consensus_timestamp               bigint not null,
    epoch_day                         bigint not null,
    max_stake_rewarded                bigint not null default 0,
    max_staking_reward_rate_per_hbar  bigint not null,
    max_total_reward                  bigint not null default 0,
    node_reward_fee_denominator       bigint not null,
    node_reward_fee_numerator         bigint not null,
    reserved_staking_rewards          bigint not null default 0,
    reward_balance_threshold          bigint not null default 0,
    stake_total                       bigint not null,
    staking_period                    bigint not null,
    staking_period_duration           bigint not null,
    staking_periods_stored            bigint not null,
    staking_reward_fee_denominator    bigint not null,
    staking_reward_fee_numerator      bigint not null,
    staking_reward_rate               bigint not null,
    staking_start_threshold           bigint not null,
    unreserved_staking_reward_balance bigint not null default 0
);
comment on table network_stake is 'Staking information common to all nodes';

-- nft
create table if not exists nft
(
    account_id         bigint,
    created_timestamp  bigint,
    delegating_spender bigint  default null,
    deleted            boolean default false,
    metadata           bytea,
    serial_number      bigint    not null,
    spender            bigint  default null,
    timestamp_range    int8range not null,
    token_id           bigint    not null
);
comment on table nft is 'Non-Fungible Tokens (NFTs) minted on network';

create table if not exists nft_history
(
    like nft including defaults
);
comment on table nft_history is 'Non-Fungible Tokens (NFTs) history state';

create table if not exists nft_allowance
(
    approved_for_all boolean   not null,
    owner            bigint    not null,
    payer_account_id bigint    not null,
    spender          bigint    not null,
    timestamp_range  int8range not null,
    token_id         bigint    not null
);
comment on table nft_allowance is 'NFT allowances delegated by owner to spender';

create table if not exists nft_allowance_history
(
    like nft_allowance including defaults
);
comment on table nft_allowance_history is 'History of NFT allowances delegated by payer to spender';

create table if not exists node_stake
(
    consensus_timestamp bigint not null,
    epoch_day           bigint not null,
    max_stake           bigint not null,
    min_stake           bigint not null,
    node_id             bigint not null,
    reward_rate         bigint not null,
    stake               bigint not null,
    stake_not_rewarded  bigint not null,
    stake_rewarded      bigint not null,
    staking_period      bigint not null
);
comment on table node_stake is 'Node staking information';

-- prng
create table if not exists prng
(
    consensus_timestamp bigint  not null,
    payer_account_id    bigint  not null,
    prng_bytes          bytea   null,
    prng_number         integer null,
    range               integer not null
);
comment on table prng is 'Pseudorandom number generator';

create table if not exists reconciliation_job
(
    consensus_timestamp bigint      not null,
    count               bigint      not null,
    error               text        not null default '',
    timestamp_end       timestamptz null,
    timestamp_start     timestamptz not null,
    status              smallint    not null
);
comment on table reconciliation_job is 'Reconciliation job status';

-- record_file
create table if not exists record_file
(
    bytes              bytea                  null,
    consensus_start    bigint                 not null,
    consensus_end      bigint                 not null,
    count              bigint                 not null,
    digest_algorithm   int                    not null,
    file_hash          character varying(96)  not null,
    gas_used           bigint                          default -1,
    hapi_version_major int,
    hapi_version_minor int,
    hapi_version_patch int,
    hash               character varying(96)  not null,
    index              bigint                 not null,
    load_start         bigint                 not null,
    load_end           bigint                 not null,
    logs_bloom         bytea                  null,
    name               character varying(250) not null,
    node_id            bigint                 not null,
    prev_hash          character varying(96)  not null,
    sidecar_count      int                    not null default 0,
    size               int                    null,
    version            int                    not null
) partition by range (consensus_end);
comment on table record_file is 'Network record file stream entries';

-- schedule
create table if not exists schedule
(
    consensus_timestamp bigint  not null,
    creator_account_id  bigint  not null,
    executed_timestamp  bigint  null,
    expiration_time     bigint  null,
    payer_account_id    bigint  not null,
    schedule_id         bigint  not null,
    transaction_body    bytea   not null,
    wait_for_expiry     boolean not null default false
);
comment on table schedule is 'Schedule entity entries';

-- sidecar file
create table if not exists sidecar_file
(
    bytes          bytea                  null,
    count          int                    null,
    consensus_end  bigint                 not null,
    hash_algorithm int                    not null,
    hash           bytea                  not null,
    id             int                    not null,
    name           character varying(250) not null,
    size           int                    null,
    types          int[]                  not null
);
comment on table sidecar_file is 'Sidecar record file';

-- staking reward transfer
create table if not exists staking_reward_transfer
(
    account_id          bigint not null,
    amount              bigint not null,
    consensus_timestamp bigint not null,
    payer_account_id    bigint not null
) partition by range (consensus_timestamp);
comment on table staking_reward_transfer is 'Staking reward transfers';

-- token
create table if not exists token
(
    created_timestamp   bigint                 not null,
    decimals            bigint                 not null,
    fee_schedule_key    bytea,
    freeze_default      boolean                not null default false,
    freeze_key          bytea,
    freeze_status       smallint               not null,
    initial_supply      bigint                 not null,
    kyc_key             bytea,
    kyc_status          smallint               not null,
    max_supply          bigint                 not null default 9223372036854775807, -- max long
    metadata            bytea,
    metadata_key        bytea,
    name                character varying(100) not null,
    pause_key           bytea                  null,
    pause_status        token_pause_status     not null default 'NOT_APPLICABLE',
    supply_key          bytea,
    supply_type         token_supply_type      not null default 'INFINITE',
    symbol              character varying(100) not null,
    timestamp_range     int8range              not null,
    token_id            bigint                 not null,
    total_supply        bigint                 not null default 0,
    treasury_account_id bigint                 not null,
    type                token_type             not null default 'FUNGIBLE_COMMON',
    wipe_key            bytea
);
comment on table token is 'Token entity';

create table token_history
(
    like token including defaults
);
comment on table token_history is 'Token entity history';

--- token_account
create table if not exists token_account
(
    account_id            bigint    not null,
    associated            boolean   not null default false,
    automatic_association boolean   null,
    balance               bigint    not null default 0,
    balance_timestamp     bigint    null,
    created_timestamp     bigint    null,
    freeze_status         smallint  null,
    kyc_status            smallint  null,
    timestamp_range       int8range not null,
    token_id              bigint    not null
);
comment on table token_account is 'Token account entity';

--- token_account_history
create table if not exists token_account_history
(
    like token_account including defaults
);
comment on table token_account_history is 'History of token_account';

create table if not exists token_allowance
(
    amount           bigint    not null,
    amount_granted   bigint    not null,
    owner            bigint    not null,
    payer_account_id bigint    not null,
    spender          bigint    not null,
    timestamp_range  int8range not null,
    token_id         bigint    not null
);
comment on table token_allowance is 'Token allowances delegated by owner to spender';

create table if not exists token_allowance_history
(
    like token_allowance including defaults
);
comment on table token_allowance_history is 'History of token allowances delegated by payer to spender';

--- token_balance
create table if not exists token_balance
(
    account_id          bigint not null,
    balance             bigint not null,
    consensus_timestamp bigint not null,
    token_id            bigint not null
) partition by range (consensus_timestamp);
comment on table token_balance is 'Crypto account token balances';

--- token_transfer
create table if not exists token_transfer
(
    account_id          bigint  not null,
    amount              bigint  not null,
    consensus_timestamp bigint  not null,
    is_approval         boolean null,
    payer_account_id    bigint  not null,
    token_id            bigint  not null
) partition by range (consensus_timestamp);
comment on table token_transfer is 'Crypto account token transfers';

-- topic_message
create table if not exists topic_message
(
    chunk_num              integer,
    chunk_total            integer,
    consensus_timestamp    bigint   not null,
    initial_transaction_id bytea,
    message                bytea    not null,
    payer_account_id       bigint   not null,
    running_hash           bytea    not null,
    running_hash_version   smallint not null,
    sequence_number        bigint   not null,
    topic_id               bigint   not null,
    valid_start_timestamp  bigint
) partition by range (consensus_timestamp);
comment on table topic_message is 'Topic entity sequenced messages';

-- topic_message_lookup
create table if not exists topic_message_lookup
(
    partition             text      not null,
    sequence_number_range int8range not null,
    timestamp_range       int8range not null,
    topic_id              bigint    not null
);
comment on table topic_message_lookup is 'Topic message lookup';

-- transaction
create table if not exists transaction
(
    charged_tx_fee             bigint,
    consensus_timestamp        bigint      not null,
    entity_id                  bigint,
    errata                     errata_type null,
    index                      integer     null,
    initial_balance            bigint               default 0,
    itemized_transfer          jsonb       null,
    max_fee                    bigint,
    memo                       bytea,
    nft_transfer               jsonb       null,
    node_account_id            bigint,
    nonce                      integer     not null default 0,
    parent_consensus_timestamp bigint      null,
    payer_account_id           bigint      not null,
    result                     smallint    not null,
    scheduled                  boolean     not null default false,
    transaction_bytes          bytea,
    transaction_hash           bytea,
    transaction_record_bytes   bytea,
    type                       smallint    not null,
    valid_start_ns             bigint      not null,
    valid_duration_seconds     bigint
) partition by range (consensus_timestamp);
comment on table transaction is 'Submitted network transactions';

-- transaction_hash
create table if not exists transaction_hash
(
    consensus_timestamp bigint not null,
    hash                bytea  not null,
    payer_account_id    bigint not null
);
comment on table transaction_hash is 'Network transaction hash to consensus timestamp mapping';

-- transaction_signature
create table if not exists transaction_signature
(
    consensus_timestamp bigint not null,
    entity_id           bigint null,
    public_key_prefix   bytea  not null,
    signature           bytea  not null,
    type                smallint
);
comment on table transaction_signature is 'Transaction signatories';
