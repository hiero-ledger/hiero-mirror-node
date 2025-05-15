alter table if exists transaction
  add column if not exists transaction_record_bytes bytea null;