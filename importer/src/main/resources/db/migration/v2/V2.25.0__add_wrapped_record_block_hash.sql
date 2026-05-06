alter table if exists record_file
  add column if not exists wrapped_record_block_hash bytea null;
