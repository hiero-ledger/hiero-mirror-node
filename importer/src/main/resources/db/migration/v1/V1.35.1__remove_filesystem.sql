-- Changes necessary to support the downloader threads no longer writing to the stream file tables and skipping the filesystem completely

-- event file
create table if not exists event_file
(
    bytes            bytea                  null,
    consensus_start  bigint                 not null,
    consensus_end    bigint primary key     not null,
    count            bigint                 not null,
    digest_algorithm int                    not null,
    file_hash        character varying(96)  not null,
    hash             character varying(96)  not null,
    load_start       bigint                 not null,
    load_end         bigint                 not null,
    name             character varying(250) not null,
    node_account_id  bigint                 not null,
    previous_hash    character varying(96)  not null,
    version          integer                not null,
    constraint event_file__hash unique (hash)
);

-- account balance file
delete
from account_balance_file
where load_end is null;

alter table if exists account_balance_file
    add column if not exists bytes bytea,
    alter column load_end set not null,
    alter column load_start set not null;

-- record file
delete
from record_file
where load_end is null;

-- First entry in the database should have zeroed out hash instead of null
update record_file
set prev_hash = '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000'
where prev_hash is null;

alter table if exists record_file
    add column if not exists bytes bytea,
    alter column file_hash set not null,
    alter column hash set not null,
    alter column load_end set not null,
    alter column load_start set not null,
    alter column prev_hash set not null;

-- application status was removed in favor of using the latest entry in the stream file tables
drop table if exists t_application_status;
