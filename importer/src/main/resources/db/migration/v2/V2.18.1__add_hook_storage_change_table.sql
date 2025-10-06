-- HIP-1195 Hook Storage Change Table
-- Stores historical changes to hook storage (similar to contract_state_change)

create table if not exists hook_storage_change
(
    consensus_timestamp bigint not null,
    hook_id             bigint not null,
    key                 bytea  not null,
    owner_id            bigint not null,
    value_read          bytea  not null,
    value_written       bytea,

    primary key (owner_id, hook_id, key, consensus_timestamp)
);
comment on table hook_storage_change is 'Historical changes to hook storage state';

select create_distributed_table('hook_storage_change', 'owner_id', colocate_with => 'entity');