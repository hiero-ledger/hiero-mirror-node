-- HIP-1195 Mirror Node Hooks Support
-- Create hook type enums and hooks table

create type hook_type as enum ('LAMBDA');
create type hook_extension_point as enum ('ACCOUNT_ALLOWANCE_HOOK');

create table if not exists hook
(
    admin_key         bytea,
    contract_id       bigint                not null,
    created_timestamp bigint                not null,
    deleted           boolean               not null default false,
    extension_point   hook_extension_point  not null default 'ACCOUNT_ALLOWANCE_HOOK',
    hook_id           bigint                not null,
    owner_id          bigint                not null,
    type              hook_type             not null default 'LAMBDA',

    primary key (owner_id, hook_id)
);
comment on table hook is 'Hooks attached to accounts and contracts';