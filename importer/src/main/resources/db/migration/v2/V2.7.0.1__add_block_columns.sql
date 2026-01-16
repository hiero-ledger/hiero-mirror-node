alter table if exists record_file
  add column if not exists software_version_major        int          null,
  add column if not exists software_version_minor        int          null,
  add column if not exists software_version_patch        int          null,
  add column if not exists round_start                   bigint       null,
  add column if not exists round_end                     bigint       null;
