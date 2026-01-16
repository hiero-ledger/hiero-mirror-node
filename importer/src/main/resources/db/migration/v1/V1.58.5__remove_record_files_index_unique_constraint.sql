-- remove the unique index on the record_file.index column
drop index if exists record_file__index;

-- add new non-unique for the record_file.index column
create index if not exists record_file__index on record_file(index);
