alter table if exists token
    add column if not exists timestamp_range int8range;

update token
set timestamp_range = int8range(modified_timestamp, null);

alter table if exists token
    alter column timestamp_range set not null,
    drop column if exists modified_timestamp;

create table token_history
(
    like token including defaults
);

create index if not exists token_history__token_id_timestamp_range
    on token_history (token_id, lower(timestamp_range));
create index if not exists token_history__timestamp_range on token_history using gist (timestamp_range);
