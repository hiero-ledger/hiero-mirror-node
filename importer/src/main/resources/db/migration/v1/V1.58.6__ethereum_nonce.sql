alter table if exists entity
    add column if not exists ethereum_nonce bigint null,
    add column if not exists evm_address bytea null;

alter table if exists entity_history
    add column if not exists ethereum_nonce bigint null,
    add column if not exists evm_address bytea null;

create index if not exists entity__evm_address on entity (evm_address) where evm_address is not null;
create index if not exists entity_history__evm_address on entity_history (evm_address) where evm_address is not null;
