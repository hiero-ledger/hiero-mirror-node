-------------------
-- Add support for token pause
-------------------


create type token_pause_status as enum ('NOT_APPLICABLE', 'PAUSED', 'UNPAUSED');

insert into t_transaction_types (proto_id, name, entity_type) values
(46, 'TOKENPAUSE', 5),
(47, 'TOKENUNPAUSE', 5);

insert into t_transaction_results (result, proto_id)
values ('TOKEN_IS_PAUSED', 265),
       ('TOKEN_HAS_NO_PAUSE_KEY', 266),
       ('INVALID_PAUSE_KEY', 267);

alter table if exists token
    add column pause_key bytea,
    add column pause_status token_pause_status not null default 'NOT_APPLICABLE';
