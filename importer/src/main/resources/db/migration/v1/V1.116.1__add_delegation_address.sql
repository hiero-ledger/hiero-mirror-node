alter table if exists entity
    add column if not exists delegation_address bytea null;

alter table if exists entity_history
    add column if not exists delegation_address bytea null;

alter table if exists ethereum_transaction
    add column if not exists authorization_list jsonb null default null;
