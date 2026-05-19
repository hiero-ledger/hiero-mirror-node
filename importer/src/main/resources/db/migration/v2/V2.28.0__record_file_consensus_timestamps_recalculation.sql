alter table if exists record_file
    add column if not exists consensus_start_calculated bigint,
    add column if not exists consensus_end_calculated bigint;
