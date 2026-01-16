alter table if exists entity
    alter column ethereum_nonce set default 0;
alter table if exists entity_history
    alter column ethereum_nonce set default 0;
