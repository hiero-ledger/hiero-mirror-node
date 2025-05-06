-- add index for queries by file id and consensus timestamp
create index if not exists file_data__id_timestamp on file_data(entity_id, consensus_timestamp);
