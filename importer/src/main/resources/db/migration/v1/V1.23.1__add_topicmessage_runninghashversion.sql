-- add the running_hash_version column conditionally
alter table if exists topic_message add column if not exists running_hash_version smallint null;

-- set rows with empty running_hash_version to 1 if service v0.5 is not yet released and to the new default value if it is
update topic_message set running_hash_version = case when consensus_timestamp < ${topicRunningHashV2AddedTimestamp} then 1 else 2 end where running_hash_version is null;

-- enforce setting of running_hash_version
alter table if exists topic_message alter column running_hash_version set not null;
