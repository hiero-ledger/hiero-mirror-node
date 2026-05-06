create table if not exists registered_node
(
    admin_key              bytea           null,
    created_timestamp      bigint          null,
    deleted                boolean         default false not null,
    description            varchar(100)    null,
    registered_node_id     bigint          not null,
    service_endpoints      jsonb           null,
    timestamp_range        int8range       not null
);

alter table if exists registered_node
    add constraint registered_node__pk primary key (registered_node_id);

create table if not exists registered_node_history
(
    like registered_node including defaults
);

create index if not exists registered_node_history__node_id_lower_timestamp
    on registered_node_history (registered_node_id, lower(timestamp_range));

alter table if exists node add column if not exists associated_registered_nodes bigint[] null;
alter table if exists node_history add column if not exists associated_registered_nodes bigint[] null;
