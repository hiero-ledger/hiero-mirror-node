alter table if exists record_file
    add column if not exists consensus_start_offset bigint,
    add column if not exists consensus_end_offset bigint;
