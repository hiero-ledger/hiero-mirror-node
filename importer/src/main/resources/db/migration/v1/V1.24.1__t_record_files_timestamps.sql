alter table if exists t_record_files
    add column if not exists consensus_start bigint not null default 0,
    add column if not exists consensus_end   bigint not null default 0;
