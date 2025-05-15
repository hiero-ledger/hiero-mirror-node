-- Enhances the entity table to track the historical state of the entity over time including the period for which it is valid

alter table if exists entity
    add column if not exists timestamp_range int8range;

update entity
set timestamp_range = int8range(coalesce(modified_timestamp, created_timestamp, 0), null);

alter table if exists entity
    alter column timestamp_range set not null,
    drop column if exists modified_timestamp;

create table if not exists entity_history
(
    like entity,
    primary key (id, timestamp_range)
);

create index if not exists entity_history__timestamp_range on entity_history using gist (timestamp_range);
