-- add node and node_history table
create table if not exists node
(
    admin_key              bytea           null,
    created_timestamp      bigint          null,
    deleted                boolean         default false not null,
    node_id                bigint          not null,
    timestamp_range        int8range       not null
);

-- add node index
alter table if exists node
    add constraint node__pk primary key (node_id);

create table if not exists node_history
(
    like node including defaults
);

create index if not exists node_history__node_id_lower_timestamp
    on node_history (node_id, lower(timestamp_range));

