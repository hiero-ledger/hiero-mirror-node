alter table if exists record_file
  add column if not exists receipts_root bytea null;
