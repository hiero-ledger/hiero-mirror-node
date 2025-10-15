-- HIP-1195 Mirror Node Hooks Support
-- Create hook type enums and all hook-related tables

-- Create enums for hook types and extension points
create type hook_type as enum ('LAMBDA');
create type hook_extension_point as enum ('ACCOUNT_ALLOWANCE_HOOK');

-- Main hook table
create table if not exists hook
(
    contract_id         bigint                not null,
    created_timestamp   bigint,
    hook_id             bigint                not null,
    owner_id            bigint                not null,
    timestamp_range     int8range,
    extension_point     hook_extension_point  not null default 'ACCOUNT_ALLOWANCE_HOOK',
    type                hook_type             not null default 'LAMBDA',
    deleted             boolean               not null default false,
    admin_key           bytea,

    primary key (owner_id, hook_id)
);
comment on table hook is 'Hooks attached to accounts and contracts';

-- Hook history table (temporal data)
create table if not exists hook_history
(
    like hook including defaults
);
comment on table hook_history is 'Historical changes to hooks';

-- Hook storage change table (historical changes)
create table if not exists hook_storage_change
(
    consensus_timestamp bigint not null,
    hook_id             bigint not null,
    owner_id            bigint not null,
    key                 bytea  not null,
    value_read          bytea  not null,
    value_written       bytea,

    primary key (owner_id, hook_id, key, consensus_timestamp)
);
comment on table hook_storage_change is 'Historical changes to hook storage state';


-- Hook storage table (current state)
create table if not exists hook_storage
(
    created_timestamp   bigint not null,
    hook_id             bigint not null,
    modified_timestamp  bigint not null,
    owner_id            bigint not null,
    key                 bytea  not null,
    value               bytea  not null,

    primary key (owner_id, hook_id, key)
);
comment on table hook_storage is 'Current state of hook storage';