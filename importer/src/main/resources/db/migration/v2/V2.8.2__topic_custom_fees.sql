 -- topic table
create table if not exists topic
(
  admin_key           bytea     null,
  created_timestamp   bigint    null,
  id                  bigint    primary key,
  fee_exempt_key_list bytea     null,
  fee_schedule_key    bytea     null,
  submit_key          bytea     null,
  timestamp_range     int8range not null
);

create table topic_history
(
  like topic including defaults
);

select create_distributed_table('topic', 'id', colocate_with => 'entity');
select create_distributed_table('topic_history', 'id', colocate_with => 'topic');

insert into topic (admin_key, created_timestamp, id, submit_key, timestamp_range)
select
  case when length(key) = 0 then null
       else key
  end,
  created_timestamp,
  id,
  case when length(submit_key) = 0 then null
       else submit_key
  end,
  timestamp_range
from entity
where type = 'TOPIC';

insert into topic_history (admin_key, created_timestamp, id, submit_key, timestamp_range)
select
  case when length(key) = 0 then null
       else key
  end,
  created_timestamp,
  id,
  case when length(submit_key) = 0 then null
       else submit_key
  end,
  timestamp_range
from entity_history
where type = 'TOPIC';

create index if not exists topic_history__timestamp_range on topic_history using gist (timestamp_range);
create index if not exists topic_history__id_lower_timestamp_range
    on topic_history (id, lower(timestamp_range));

alter table if exists entity drop column if exists submit_key;
alter table if exists entity_history drop column if exists submit_key;

-- custom_fee table
alter table if exists custom_fee rename column token_id to entity_id;
alter table if exists custom_fee_history rename column token_id to entity_id;

-- backfill custom fee for topics with empty fixed fees
insert into custom_fee (fixed_fees, entity_id, timestamp_range)
select
  '[]'::jsonb,
  id,
  int8range(coalesce(created_timestamp, 0), null)
from topic;

-- transaction table
alter table if exists transaction add column if not exists max_custom_fees bytea[];
