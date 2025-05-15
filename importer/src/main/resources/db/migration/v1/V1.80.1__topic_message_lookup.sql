create table if not exists topic_message_lookup (
  partition             text      not null,
  sequence_number_range int8range not null,
  timestamp_range       int8range not null,
  topic_id              bigint    not null,
  primary key (topic_id, partition)
);

-- skip the index for v1 since it requires the extension btree_gist and topic_message table isn't partitioned
-- create index if not exists topic_message_lookup__topic_sequence_number_range
--   on topic_message_lookup using gist (topic_id, sequence_number_range);
