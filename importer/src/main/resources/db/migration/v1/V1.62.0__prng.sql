create table if not exists prng
(
    consensus_timestamp bigint  not null,
    range               integer not null,
    prng_bytes  bytea   null,
    prng_number integer null,
    primary key (consensus_timestamp)
)
