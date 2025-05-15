create table if not exists reconciliation_job
(
    consensus_timestamp bigint      not null,
    count               bigint      not null,
    error               text        not null default '',
    timestamp_end       timestamptz null,
    timestamp_start     timestamptz not null,
    status              smallint    not null,
    primary key (timestamp_start)
);
