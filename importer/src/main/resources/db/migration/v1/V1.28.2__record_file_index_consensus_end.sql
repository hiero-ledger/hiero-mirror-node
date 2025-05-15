-- create an index on record_file.consensus_end, the index can be used to find the record file which contains a
-- specific transaction by its consensus timestamp
create index if not exists record_file__consensus_end on record_file (consensus_end);
