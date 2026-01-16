alter table if exists topic_message
  alter column running_hash_version drop not null;
