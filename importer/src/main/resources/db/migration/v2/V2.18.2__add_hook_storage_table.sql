-- HIP-1195 Hook Storage Table
-- Stores current state of hook storage (similar to contract_state)

create table if not exists hook_storage
(
    consensus_timestamp bigint not null,
    hook_id             bigint not null,
    key                 bytea  not null,
    owner_id            bigint not null,
    value               bytea  not null,

    primary key (owner_id, hook_id, key)
);
comment on table hook_storage is 'Current state of hook storage';

select create_distributed_table('hook_storage', 'owner_id', colocate_with => 'entity');
