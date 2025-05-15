--
-- Permissions
--
alter default privileges in schema public
    grant select on tables to ${api-user};
alter default privileges in schema public
    grant all on tables to ${db-user};

--
-- Type aliases
--
create domain entity_type_id as char;
create domain entity_realm_num as smallint;
create domain entity_num as int;
create type entity_id as (
    type_id entity_type_id
    ,realm_num entity_realm_num
    ,num entity_num
);

create domain nanos_timestamp as bigint;
create domain hbar_tinybars as bigint; -- bigint representing hashbar tinybars

--
-- Reference data tables
--
create table entity_types (
    id entity_type_id
    ,name varchar

    ,constraint pk__entity_types primary key (id)
);
insert into entity_types (id, name)
    values ('a', 'account')
    ,('f', 'file')
    ,('c', 'contract');

--
-- Insert-heavy tables.
--

-- Account balances in tinybars at a particular point in time.
create table account_balances (
    consensus_timestamp nanos_timestamp not null

    ,account_id entity_id not null

    ,balance hbar_tinybars not null

    ,constraint pk__account_balances primary key (consensus_timestamp, account_id)
);
comment on table account_balances is 'account balances (historical) in tinybars at different consensus timestamps';
create index idx__account_balances__account_then_timestamp
    on account_balances (account_id, consensus_timestamp desc);

--
-- Snapshot/file processing tables.
-- Read frequently, updated once.
--

-- Complete sets of processed account balances for a given consensus timestamp.
create table account_balance_sets (
    consensus_timestamp nanos_timestamp not null

    ,is_complete boolean not null default false
    ,processing_start_timestamp timestamp without time zone null default (now() at time zone 'utc')
    ,processing_end_timestamp timestamp without time zone null

    ,constraint pk__account_balance_sets primary key (consensus_timestamp)
);
comment on table account_balance_sets is 'processing state of snapshots of the entire set of account balances at different consensus timestamps';
create index idx__account_balance_sets__completed
    on account_balance_sets (is_complete, consensus_timestamp desc);
