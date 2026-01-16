-------------------
-- Support schedule transactions
-------------------

-- Add new schedule entity type
insert into t_entity_types (id, name)
values (6, 'schedule');

-- Add new schedule transaction body types
insert into t_transaction_types (proto_id, name, entity_type)
values (42, 'SCHEDULECREATE', 6),
       (43, 'SCHEDULEDELETE', 6),
       (44, 'SCHEDULESIGN', 6);

-- Add schedule transaction result types
insert into t_transaction_results (proto_id, result)
values (201, 'INVALID_SCHEDULE_ID'),
       (202, 'SCHEDULE_IS_IMMUTABLE'),
       (203, 'SCHEDULE_WAS_DELETED'),
       (204, 'INVALID_SCHEDULE_PAYER_ID'),
       (205, 'INVALID_SCHEDULE_ACCOUNT_ID');

-- Add schedule table to hold schedule properties
create table if not exists schedule
(
    consensus_timestamp bigint primary key not null,
    creator_account_id  bigint             not null,
    executed_timestamp  bigint             null,
    payer_account_id    bigint             not null,
    schedule_id         bigint             not null,
    transaction_body    bytea              not null
);
comment on table schedule is 'Schedule entity entries';

create unique index if not exists schedule__schedule_id
    on schedule (schedule_id desc);

--- Add schedule_signature table to capture schedule signatories
create table if not exists schedule_signature
(
    consensus_timestamp bigint not null,
    public_key_prefix   bytea  not null,
    schedule_id         bigint not null,
    signature           bytea  not null
);
comment on table schedule is 'Schedule transaction signatories';

create index if not exists schedule_signature__schedule_id
    on schedule_signature (schedule_id desc);

create unique index if not exists schedule_signature__timestamp_public_key_prefix
    on schedule_signature (consensus_timestamp desc, public_key_prefix);
