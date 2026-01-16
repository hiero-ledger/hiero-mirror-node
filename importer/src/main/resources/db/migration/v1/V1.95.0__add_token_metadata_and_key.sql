-- Support token metadata, HIP-646/657/765
alter table if exists token
  add column if not exists metadata bytea null,
  add column if not exists metadata_key bytea null;

alter table if exists token_history
  add column if not exists metadata bytea null,
  add column if not exists metadata_key bytea null;
