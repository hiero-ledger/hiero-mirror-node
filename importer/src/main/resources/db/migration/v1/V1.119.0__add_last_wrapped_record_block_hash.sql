alter table if exists record_file
  add column if not exists last_wrapped_record_block_hash varchar(96) null;
