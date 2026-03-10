alter table if exists record_file
  add column if not exists previous_wrapped_record_block_hash bytea null;
